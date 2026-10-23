package app.pillzilla.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import app.pillzilla.R
import app.pillzilla.data.Reminder
import app.pillzilla.ui.AlarmActivity
import app.pillzilla.ui.MainActivity

class Notifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val alarm = NotificationChannel(CHANNEL_ALARM, "Medication alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings until you scan the tag next to your meds"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val info = NotificationChannel(CHANNEL_INFO, "Confirmations and missed doses", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannels(listOf(alarm, info))
    }

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()

    fun areEnabled(): Boolean = manager.areNotificationsEnabled()

    fun showAlarm(reminder: Reminder, scheduledAt: Long, step: Int) {
        val fullScreen = PendingIntent.getActivity(
            context, reminder.id,
            AlarmActivity.intent(context, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val overdueMin = ((System.currentTimeMillis() - scheduledAt) / Schedule.MINUTE).coerceAtLeast(0)
        val tag = reminder.tagName ?: "tag"
        val text = if (step == 0) {
            "Time for your meds. Scan your $tag to confirm."
        } else {
            "Overdue $overdueMin min. Unlock, then hold your $tag to the phone."
        }
        val notification = Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${reminder.name} reminder")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
        manager.notify(alarmId(reminder.id), notification)
    }

    fun cancelAlarm(reminderId: Int) = manager.cancel(alarmId(reminderId))

    fun showConfirmed(reminder: Reminder, points: Int) {
        val text = if (points > 0) "Tag scanned. +$points points." else "Tag scanned. Nudges stopped."
        notifyInfo(infoId(reminder.id), "${reminder.name} confirmed", text)
    }

    fun showMissed(reminder: Reminder) {
        notifyInfo(infoId(reminder.id), "${reminder.name} dose missed", "No scan within 2 h 30 min. Your streak was reset.")
    }

    private fun notifyInfo(id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    private fun alarmId(reminderId: Int) = 100 + reminderId
    private fun infoId(reminderId: Int) = 200 + reminderId

    companion object {
        const val CHANNEL_ALARM = "alarm"
        const val CHANNEL_INFO = "info"
    }
}
