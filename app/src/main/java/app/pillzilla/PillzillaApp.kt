package app.pillzilla

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PillzillaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val app = Pillzilla.get(this)
        app.notifier.createChannels()
        appScope.launch {
            app.ensureSeeded()
            app.rescheduleAll()
        }
    }
}
