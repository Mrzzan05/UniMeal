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

data class Settings(
    val username: String = "",
    val password: String = "",
    val lunch: Boolean = true,
    val dinner: Boolean = true,
    val selfId: String = "12",
    /** Look and report, but never confirm a reservation. On until the user turns it off. */
    val preview: Boolean = true,
    val waitNextWeek: Boolean = true,
    /** Most rials one run may spend, 0 = no limit. */
    val maxSpend: Long = 0L,
) {
    val hasLogin: Boolean get() = username.isNotBlank() && password.isNotEmpty()
    val meals: Set<Meal> get() = buildSet { if (lunch) add(Meal.LUNCH); if (dinner) add(Meal.DINNER) }
}

/** Encrypted settings file. The password only exists here (encrypted) and in memory while a run is active. */
class Vault(ctx: Context) {
    private val file = File(ctx.filesDir, "unimeal.dat")

    fun load(): Settings = try {
        if (!file.exists()) Settings() else {
            val j = JSONObject(String(Crypto.decrypt(file.readBytes()), Charsets.UTF_8))
            Settings(
                j.optString("u"), j.optString("p"), j.optBoolean("l", true), j.optBoolean("d", true),
                j.optString("s", "12"), j.optBoolean("pv", true), j.optBoolean("w", true), j.optLong("m", 0L),
            )
        }
    } catch (_: Exception) { Settings() }

    fun save(s: Settings) {
        try {
            val j = JSONObject().put("u", s.username).put("p", s.password).put("l", s.lunch).put("d", s.dinner)
                .put("s", s.selfId).put("pv", s.preview).put("w", s.waitNextWeek).put("m", s.maxSpend)
            val tmp = File(file.parentFile, "unimeal.tmp")
            tmp.writeBytes(Crypto.encrypt(j.toString().toByteArray(Charsets.UTF_8)))
            if (!tmp.renameTo(file)) { file.writeBytes(tmp.readBytes()); tmp.delete() }
        } catch (_: Exception) { /* keep the in-memory copy */ }
    }

    fun wipe() { file.delete() }
}
