package ai.pleos.challenge.arrival

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the process running while another app, usually navigation, owns the screen. Without it Android freezes the
 * hidden app, and gear, odometer and GPS callbacks only arrive once it is opened again. [MainActivity] still owns
 * the recording; this service holds the process and shows that recording is on.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notices.recording(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notices.RECORDING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Notices.RECORDING_ID, notification)
        // The recording lives in the activity, so a restart without it would have nothing to keep alive.
        return START_NOT_STICKY
    }
}

/** Notifications: the ongoing "recording" one, and the prompt shown over other apps when the car is parked. */
object Notices {
    const val RECORDING_ID = 1
    private const val ARRIVAL_ID = 2
    private const val RECORDING_CHANNEL = "recording"
    private const val ARRIVAL_CHANNEL = "arrival"

    /** Extra on the intent that opens [MainActivity] from a notification, naming what to start. */
    const val EXTRA_ACTION = "notice_action"
    const val ACTION_LOGBOOK = "logbook"
    const val ACTION_PARK = "park"
    const val ACTION_SPEAK = "speak"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun createChannels(context: Context) {
        manager(context).createNotificationChannels(
            listOf(
                NotificationChannel(RECORDING_CHANNEL, "운행 기록 중", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(ARRIVAL_CHANNEL, "도착 알림", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    private fun open(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (action != null) intent.putExtra(EXTRA_ACTION, action)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun recording(context: Context): Notification {
        createChannels(context)
        return Notification.Builder(context, RECORDING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("도착노트")
            .setContentText("운행을 기록하고 있어요.")
            .setContentIntent(open(context, null, 0))
            .setOngoing(true)
            .build()
    }

    /** Parked after a drive while another app is in front: offer the next step without taking over the screen. */
    fun showArrival(context: Context, distance: String) {
        createChannels(context)
        val notification = Notification.Builder(context, ARRIVAL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("운행을 기록할까요?")
            .setContentText("방금 운행 $distance · 누르면 바로 정리해요.")
            .setContentIntent(open(context, ACTION_LOGBOOK, 1))
            .addAction(Notification.Action.Builder(null, "기록하기", open(context, ACTION_LOGBOOK, 1)).build())
            .addAction(Notification.Action.Builder(null, "주차만 기록", open(context, ACTION_PARK, 2)).build())
            .addAction(Notification.Action.Builder(null, "말하기", open(context, ACTION_SPEAK, 3)).build())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        runCatching { manager(context).notify(ARRIVAL_ID, notification) }
    }

    fun cancelArrival(context: Context) = manager(context).cancel(ARRIVAL_ID)
}
