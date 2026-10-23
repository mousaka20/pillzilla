package app.pillzilla.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import java.io.IOException

object TagIo {
    /** MIME type of the record Pillzilla writes. The manifest routes it to [NfcScanActivity]. */
    const val MIME = "application/vnd.pillzilla.tag"

    private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V

    /** The tag's serial number as colon separated hex, for example 04:A2:19:7B:3C:5D:80. */
    fun uidOf(tag: Tag): String = tag.id.joinToString(":") { "%02X".format(it) }

    fun isAvailable(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun isEnabled(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    /** Reads tags directly while [activity] is in the foreground, without system sounds or app choosers. */
    fun enableReader(activity: Activity, onTag: (Tag) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        adapter.enableReaderMode(activity, { tag -> onTag(tag) }, READER_FLAGS, extras)
    }

    fun disableReader(activity: Activity) {
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
    }

    /**
     * Writes our MIME record plus an Android Application Record (about 60 bytes, NTAG213 holds 144),
     * so Android opens Pillzilla directly when the tag is scanned, even if the app is closed.
     * Returns false if the tag can't be written; the UID still works while the app is open.
     */
    fun writeMarker(tag: Tag, packageName: String): Boolean {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createMime(MIME, "pillzilla".toByteArray()),
                NdefRecord.createApplicationRecord(packageName),
            ),
        )
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                ndef.use {
                    if (!it.isWritable || it.maxSize < message.byteArrayLength) return false
                    it.writeNdefMessage(message)
                }
                true
            } else {
                val formatable = NdefFormatable.get(tag) ?: return false
                formatable.connect()
                formatable.use { it.format(message) }
                true
            }
        } catch (e: IOException) {
            false
        } catch (e: android.nfc.FormatException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
