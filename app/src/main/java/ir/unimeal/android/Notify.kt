package ir.unimeal.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notify {
    const val CH_RUN = "run"
    const val CH_RESULT = "result"
    const val CH_REMIND = "remind"
    const val ID_RUN = 1
    const val ID_REMIND = 2
    private var next = 100

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, ctx.getString(R.string.ch_run), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, ctx.getString(R.string.ch_result), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, ctx.getString(R.string.ch_remind), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun running(ctx: Context, text: String) = NotificationCompat.Builder(ctx, CH_RUN)
        .setSmallIcon(R.drawable.ic_stat).setContentTitle(ctx.getString(R.string.app_name))
        .setContentText(text).setOngoing(true).setContentIntent(open(ctx)).setOnlyAlertOnce(true).build()

    fun updateRunning(ctx: Context, text: String) {
        if (canPost(ctx)) NotificationManagerCompat.from(ctx).notify(ID_RUN, running(ctx, text))
    }

    /** Opens the in-app browser, signed in as [accountId] (or the default account). */
    fun siteIntent(ctx: Context, accountId: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, 1 + (accountId?.hashCode() ?: 0).and(0xFFFF), SamadActivity.intent(ctx, accountId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun reminder(ctx: Context) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_REMIND).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.n_remind_title)).setContentText(ctx.getString(R.string.n_remind_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.n_remind_text)))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open(ctx)).build()
        NotificationManagerCompat.from(ctx).notify(ID_REMIND, n)
    }

    /**
     * Results the user must see: reserved, problems, finished. Never contains a password.
     * With [openSite] a tap opens the SAMAD reservation page, so the user can finish by hand.
     */
    fun result(ctx: Context, title: String, text: String, openSite: Boolean = false, accountId: String? = null) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_RESULT).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
        if (openSite) {
            b.setContentIntent(siteIntent(ctx, accountId))
            b.addAction(0, ctx.getString(R.string.open_samad), siteIntent(ctx, accountId))
            b.addAction(0, ctx.getString(R.string.open_app), open(ctx))
        } else {
            b.setContentIntent(open(ctx))
        }
        NotificationManagerCompat.from(ctx).notify(next++, b.build())
    }

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33
}
