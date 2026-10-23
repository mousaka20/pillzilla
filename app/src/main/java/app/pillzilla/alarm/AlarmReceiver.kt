package app.pillzilla.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pillzilla.Pillzilla
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER, -1)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0)
        val step = intent.getIntExtra(AlarmScheduler.EXTRA_STEP, 0)
        if (reminderId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Pillzilla.get(context).onAlarm(reminderId, scheduledAt, step)
            } finally {
                pending.finish()
            }
        }
    }
}
