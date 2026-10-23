package app.pillzilla.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pillzilla.Pillzilla
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot or a clock change, so they are rebuilt from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Pillzilla.get(context).rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
