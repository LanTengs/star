package com.lanteng.star.app.ui

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.data.TagInfo
import com.lanteng.star.app.nfc.TagReader
import com.lanteng.star.app.util.Dates
import com.google.android.material.appbar.MaterialToolbar

/**
 * Read-only NFC capture screen.
 *
 * Reader mode is enabled with FLAG_READER_NO_PLATFORM_SOUNDS so the framework
 * hands us the tag directly; nothing here can write to a tag.
 */
class NfcReadActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var adapter: NfcAdapter? = null
    private var lastRead: TagInfo? = null

    /** The link carried by the user's own card, captured while it is in the field. */
    private var lastUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_read)

        adapter = NfcAdapter.getDefaultAdapter(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        findViewById<Button>(R.id.btn_rescan).setOnClickListener {
            lastRead = null
            lastUri = null
            render(null)
        }
        findViewById<Button>(R.id.btn_attach).setOnClickListener { attach() }

        if (adapter == null) {
            findViewById<TextView>(R.id.tv_status).setText(R.string.nfc_unavailable)
        }
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter ?: return
        if (!nfc.isEnabled) {
            findViewById<TextView>(R.id.tv_status).setText(R.string.nfc_disabled)
            return
        }
        nfc.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: android.nfc.Tag?) {
        if (tag == null) return
        // The transceive/NDEF calls must not run on the reader callback thread,
        // and they have to happen while the card is still in the field.
        Thread {
            val info = runCatching { TagReader.read(tag) }.getOrNull()
            val uri = runCatching { TagReader.firstUri(tag) }.getOrNull()
            runOnUiThread {
                lastRead = info
                lastUri = uri
                render(info)
            }
        }.start()
    }

    private fun render(info: TagInfo?) {
        val status = findViewById<TextView>(R.id.tv_status)
        val details = findViewById<View>(R.id.tag_details)

        if (info == null) {
            status.setText(R.string.nfc_hint)
            details.visibility = View.GONE
            return
        }

        status.text = getString(R.string.badge_tag_uid) + ": " + info.uid
        details.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_uid).text =
            getString(R.string.badge_tag_uid) + ": " + info.uid
        findViewById<TextView>(R.id.tv_tech).text =
            getString(R.string.badge_tag_tech) + ": " + info.techList.joinToString(", ")
        findViewById<TextView>(R.id.tv_ndef).text =
            getString(R.string.badge_tag_ndef) + ": " + (info.ndef ?: "\u2014")
        findViewById<TextView>(R.id.tv_pages).text = info.pages.orEmpty()
    }

    private fun attach() {
        val info = lastRead
        if (info == null) {
            Toast.makeText(this, R.string.nfc_hint, Toast.LENGTH_SHORT).show()
            return
        }

        val badgeId = intent.getStringExtra(EXTRA_BADGE_ID)
        if (!badgeId.isNullOrBlank()) {
            // Attach the observation, and the link if this card carries one, to
            // the badge that asked for it.
            Store.findBadge(badgeId)?.let { badge ->
                badge.tag = info
                lastUri?.let { badge.badgeUrl = it }
                Store.upsert(badge)
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        // No target badge: create one from the observation so a read is never lost.
        val badge = com.lanteng.star.app.data.Badge(
            id = Store.newId(),
            name = getString(R.string.badge_tag) + " " + info.uid.takeLast(6),
            category = Store.defaultCategories.last(),
            accountId = Store.accounts.firstOrNull()?.id.orEmpty(),
            acquiredAt = null,
            notes = getString(R.string.badge_tag_read_at) + ": " + Dates.stamp(info.readAt),
            imageUri = null,
            tag = info,
            badgeUrl = lastUri,
        )
        Store.upsert(badge)
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_BADGE_ID = "com.lanteng.star.app.extra.BADGE_ID"
    }
}
