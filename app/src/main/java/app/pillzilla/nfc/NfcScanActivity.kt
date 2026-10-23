package app.pillzilla.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.pillzilla.Pillzilla
import app.pillzilla.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible activity that Android starts when a tag is scanned while Pillzilla is not in the
 * foreground. It checks the tag's UID, clears the matching reminder and closes right away.
 */
class NfcScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag == null) {
            finish()
            return
        }
        val uid = TagIo.uidOf(tag)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { Pillzilla.get(this@NfcScanActivity).handleScan(uid) }
            val message = when (result) {
                is ScanResult.Confirmed -> buildString {
                    append(result.reminder.name).append(" confirmed")
                    if (result.points > 0) append(" · +").append(result.points).append(" points")
                }
                is ScanResult.NothingDue -> "${result.reminder.name}: nothing to confirm right now"
                ScanResult.UnknownTag -> null
            }
            message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show() }
            finish()
        }
    }
}
