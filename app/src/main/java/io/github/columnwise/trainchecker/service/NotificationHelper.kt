package io.github.columnwise.trainchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.columnwise.trainchecker.MainActivity
import io.github.columnwise.trainchecker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    companion object {
        const val CHANNEL_WATCHING = "watching"
        const val CHANNEL_RESULT = "result"
        const val NOTIF_ID_WATCHING = 1001
        const val NOTIF_ID_RESULT = 1002
    }

    init {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCHING, "감시 중", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "예약 결과", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun buildWatchingNotification(summary: String): Notification {
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle("취소표 감시 중")
            .setContentText(summary)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    fun notifySuccess(title: String, detail: String) {
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_RESULT, notif)
    }

    fun notifyError(message: String) {
        val notif = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle("오류 발생")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_RESULT + 1, notif)
    }
}
