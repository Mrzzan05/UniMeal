package ir.unimeal.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** AES-256-GCM with a key that lives in the Android Keystore and never leaves it. */
private object Crypto {
    private const val ALIAS = "unimeal_store_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN))
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }
}

data class Account(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String = "",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
) {
    val usable: Boolean get() = enabled && username.isNotBlank() && password.isNotEmpty()
    val shownName: String get() = label.ifBlank { username.takeLast(3).let { "…$it" } }
}

data class Settings(
    val accounts: List<Account> = emptyList(),
    val lunch: Boolean = true,
    val dinner: Boolean = true,
    val selfId: String = "12",
    /** Look and report, but never confirm a reservation. On until the user turns it off. */
    val preview: Boolean = true,
    val waitNextWeek: Boolean = true,
    /** Most rials one run may spend per account, 0 = no limit. */
    val maxSpend: Long = 0L,
    /** The account "Open SAMAD" signs in with. */
    val defaultAccountId: String = "",
    /** 0 = follow the system, 1 = light, 2 = dark. */
    val theme: Int = 0,
    val reminderOn: Boolean = false,
    /** ISO day: 1 = Monday … 7 = Sunday. Wednesday by default. */
    val reminderDay: Int = 3,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    /** Seconds between checks while waiting for a menu. */
    val pollSeconds: Int = 60,
) {
    val usableAccounts: List<Account> get() = accounts.filter { it.usable }
    val defaultAccount: Account?
        get() = accounts.firstOrNull { it.id == defaultAccountId && it.username.isNotBlank() && it.password.isNotEmpty() }
            ?: accounts.firstOrNull { it.username.isNotBlank() && it.password.isNotEmpty() }
    val meals: Set<Meal> get() = buildSet { if (lunch) add(Meal.LUNCH); if (dinner) add(Meal.DINNER) }
}

/** Encrypted settings file. Passwords only exist here (encrypted) and in memory while a run is active. */
class Vault(ctx: Context) {
    private val file = File(ctx.filesDir, "unimeal.dat")

    fun load(): Settings = try {
        if (!file.exists()) Settings() else {
            val j = JSONObject(String(Crypto.decrypt(file.readBytes()), Charsets.UTF_8))
            val list = ArrayList<Account>()
            j.optJSONArray("a")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    list += Account(a.optString("i"), a.optString("n"), a.optString("u"), a.optString("p"), a.optBoolean("e", true))
                }
            }
            // Version 0.1.0 stored a single account.
            if (list.isEmpty() && j.optString("u").isNotBlank()) list += Account(label = "", username = j.optString("u"), password = j.optString("p"))
            Settings(
                list, j.optBoolean("l", true), j.optBoolean("d", true),
                j.optString("s", "12"), j.optBoolean("pv", true), j.optBoolean("w", true), j.optLong("m", 0L),
                j.optString("da"), j.optInt("th", 0), j.optBoolean("ro", false), j.optInt("rd", 3),
                j.optInt("rh", 20), j.optInt("rm", 0), j.optInt("ps", 60),
            )
        }
    } catch (_: Exception) { Settings() }

    fun save(s: Settings) {
        try {
            val arr = org.json.JSONArray()
            for (a in s.accounts) arr.put(JSONObject().put("i", a.id).put("n", a.label).put("u", a.username).put("p", a.password).put("e", a.enabled))
            val j = JSONObject().put("a", arr).put("l", s.lunch).put("d", s.dinner)
                .put("s", s.selfId).put("pv", s.preview).put("w", s.waitNextWeek).put("m", s.maxSpend)
                .put("da", s.defaultAccountId).put("th", s.theme).put("ro", s.reminderOn).put("rd", s.reminderDay)
                .put("rh", s.reminderHour).put("rm", s.reminderMinute).put("ps", s.pollSeconds)
            val tmp = File(file.parentFile, "unimeal.tmp")
            tmp.writeBytes(Crypto.encrypt(j.toString().toByteArray(Charsets.UTF_8)))
            if (!tmp.renameTo(file)) { file.writeBytes(tmp.readBytes()); tmp.delete() }
        } catch (_: Exception) { /* keep the in-memory copy */ }
    }

    fun wipe() { file.delete() }
}
