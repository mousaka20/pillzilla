package app.pillzilla.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pillzilla.ui.MainActivity

/** Thin wrapper around AlarmManager. Each reminder owns exactly one pending alarm at a time. */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    fun set(reminderId: Int, scheduledAt: Long, step: Int, triggerAt: Long) {
        val operation = alarmIntent(reminderId, scheduledAt, step)
        if (canScheduleExact()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(reminderId: Int) {
        alarmManager.cancel(alarmIntent(reminderId, 0, 0))
    }

    private fun alarmIntent(reminderId: Int, scheduledAt: Long, step: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_REMINDER, reminderId)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            .putExtra(EXTRA_STEP, step)
        return PendingIntent.getBroadcast(
            context, reminderId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "app.pillzilla.FIRE"
        const val EXTRA_REMINDER = "reminderId"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val EXTRA_STEP = "step"
    }
}
