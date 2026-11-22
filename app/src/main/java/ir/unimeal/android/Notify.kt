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
    const val ID_RUN = 1
    private var next = 100

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, ctx.getString(R.string.ch_run), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, ctx.getString(R.string.ch_result), NotificationManager.IMPORTANCE_HIGH))
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

    /** Results the user must see: reserved, problems, finished. Never contains a password. */
    fun result(ctx: Context, title: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_RESULT).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open(ctx)).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(ctx).notify(next++, n)
    }

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33
}
