package ir.unimeal.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/** Weekly "press Start" reminder. A normal (inexact-allowed) alarm is enough: a few minutes of slack is fine. */
object Reminder {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!s.reminderOn) { am.cancel(pending(ctx)); return }
        val at = nextReminder(ZonedDateTime.now(), s.reminderDay, s.reminderHour, s.reminderMinute)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(ctx))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = Vault(ctx).load()
        if (s.reminderOn) {
            Notify.channels(ctx)
            Notify.reminder(ctx)
            Reminder.schedule(ctx, s)
        }
    }
}

/** Puts the alarm back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.schedule(ctx, Vault(ctx).load())
        }
    }
}
