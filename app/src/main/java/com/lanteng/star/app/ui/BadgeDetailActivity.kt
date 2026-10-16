package com.lanteng.star.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Badge
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.launch.BadgeLauncher
import com.lanteng.star.app.qr.QrBitmap
import com.lanteng.star.app.qr.QrCodec
import com.lanteng.star.app.util.Dates
import com.lanteng.star.app.util.Prefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BadgeDetailActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var badge: Badge? = null
    private var linkRevealed = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            badge?.let { bind(it) }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_badge_detail)
        prefs = Prefs(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val item = Store.findBadge(intent.getStringExtra(EXTRA_BADGE_ID).orEmpty())
        if (item == null) {
            finish()
            return
        }
        badge = item
        bind(item)
        handler.postDelayed(ticker, 1000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun bind(item: Badge) {
        findViewById<TextView>(R.id.tv_name).text =
            item.name.ifBlank { getString(R.string.badge_name) }

        findViewById<TextView>(R.id.tv_category).text =
            getString(R.string.badge_category) + ": " + item.category.ifBlank { "\u2014" }

        findViewById<TextView>(R.id.tv_account).text =
            getString(R.string.badge_account) + ": " +
                Store.accountName(item.accountId).ifBlank { "\u2014" }

        findViewById<TextView>(R.id.tv_acquired).text =
            getString(R.string.badge_acquired_at) + ": " +
                Dates.day(item.acquiredAt).ifBlank { "\u2014" }

        val notes = findViewById<TextView>(R.id.tv_notes)
        notes.text = item.notes
        notes.visibility = if (item.notes.isBlank()) View.GONE else View.VISIBLE

        val image = findViewById<ImageView>(R.id.badge_image)
        val uri = item.imageUri
        if (uri.isNullOrBlank()) {
            image.setImageResource(R.drawable.ic_badge_placeholder)
        } else {
            runCatching { image.setImageURI(Uri.parse(uri)) }
                .onFailure { image.setImageResource(R.drawable.ic_badge_placeholder) }
        }

        bindLink(item)
        bindTag(item)

        findViewById<Button>(R.id.btn_edit).setOnClickListener {
            BadgeEditDialog.show(this, item) { onResume() }
        }
        findViewById<Button>(R.id.btn_delete).setOnClickListener { confirmDelete(item) }
        findViewById<Button>(R.id.btn_show_qr).setOnClickListener { showQr(item) }
    }

    private fun bindLink(item: Badge) {
        val linkNone = findViewById<TextView>(R.id.tv_link_none)
        val linkDetails = findViewById<View>(R.id.link_details)
        val linkText = findViewById<TextView>(R.id.tv_link)
        val linkStatus = findViewById<TextView>(R.id.tv_link_status)

        val url = item.badgeUrl
        val hasLink = !url.isNullOrBlank()

        linkNone.visibility = if (hasLink) View.GONE else View.VISIBLE
        linkDetails.visibility = if (hasLink) View.VISIBLE else View.GONE

        if (hasLink) {
            linkText.text = if (linkRevealed) url else mask(url!!)

            val effect = BadgeLauncher.effectRemainingMs(item, prefs)
            val remaining = BadgeLauncher.cooldownRemainingMs(item, prefs)
            linkStatus.text = when {
                effect > 0 -> getString(R.string.effect_active, BadgeLauncher.format(effect))
                remaining > 0 -> getString(
                    R.string.cooldown_waiting,
                    BadgeLauncher.format(remaining),
                )
                else -> getString(R.string.cooldown_ready)
            }
        }

        findViewById<Button>(R.id.btn_reveal_link).apply {
            visibility = if (hasLink) View.VISIBLE else View.GONE
            setText(if (linkRevealed) R.string.badge_link_hide else R.string.badge_link_show)
            setOnClickListener {
                linkRevealed = !linkRevealed
                bindLink(item)
            }
        }
        findViewById<Button>(R.id.btn_paste_link).apply {
            setText(if (hasLink) R.string.badge_link_replace else R.string.badge_link_paste)
            setOnClickListener { promptForLink(item) }
        }
        findViewById<Button>(R.id.btn_read_link).setOnClickListener {
            startActivity(
                Intent(this, NfcReadActivity::class.java)
                    .putExtra(NfcReadActivity.EXTRA_BADGE_ID, item.id),
            )
        }
        findViewById<Button>(R.id.btn_trigger).apply {
            isEnabled = hasLink && BadgeLauncher.isReady(item, prefs)
            setOnClickListener { deliver(item) }
        }
    }

    private fun deliver(item: Badge) {
        if (item.badgeUrl.isNullOrBlank()) {
            toast(R.string.trigger_no_link)
            return
        }
        if (BadgeLauncher.trigger(this, item, prefs)) {
            bind(item)
        } else {
            toast(R.string.trigger_failed)
        }
    }

    private fun promptForLink(item: Badge) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://"
            setText(item.badgeUrl.orEmpty())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.badge_link)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty() &&
                    !value.startsWith("http://") &&
                    !value.startsWith("https://")
                ) {
                    toast(R.string.badge_link_invalid)
                    return@setPositiveButton
                }
                item.badgeUrl = value.ifEmpty { null }
                Store.upsert(item)
                toast(R.string.badge_link_saved)
                bind(item)
            }
            .show()
    }

    private fun bindTag(item: Badge) {
        val tagNone = findViewById<TextView>(R.id.tv_tag_none)
        val tagDetails = findViewById<View>(R.id.tag_details)
        val tag = item.tag

        if (tag == null) {
            tagNone.visibility = View.VISIBLE
            tagDetails.visibility = View.GONE
            tagNone.setOnClickListener {
                startActivity(
                    Intent(this, NfcReadActivity::class.java)
                        .putExtra(NfcReadActivity.EXTRA_BADGE_ID, item.id),
                )
            }
        } else {
            tagNone.visibility = View.GONE
            tagDetails.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_uid).text =
                getString(R.string.badge_tag_uid) + ": " + tag.uid
            findViewById<TextView>(R.id.tv_tech).text =
                getString(R.string.badge_tag_tech) + ": " + tag.techList.joinToString(", ")
            findViewById<TextView>(R.id.tv_ndef).text =
                getString(R.string.badge_tag_ndef) + ": " + (tag.ndef ?: "\u2014")
            findViewById<TextView>(R.id.tv_pages).text = tag.pages.orEmpty()
            findViewById<TextView>(R.id.tv_read_at).text =
                getString(R.string.badge_tag_read_at) + ": " + Dates.stamp(tag.readAt)
        }
    }

    private fun confirmDelete(item: Badge) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(item.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                Store.deleteBadge(item.id)
                toast(R.string.toast_deleted)
                finish()
            }
            .show()
    }

    private fun showQr(item: Badge) {
        val bitmap = QrBitmap.render(QrCodec.encode(item))
        if (bitmap == null) {
            toast(R.string.qr_bad_payload)
            return
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            setPadding(24, 24, 24, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.qr_show)
            .setView(image)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Keeps the link out of sight until the owner explicitly reveals it. */
    private fun mask(url: String): String {
        val tail = if (url.length > 6) url.takeLast(6) else ""
        val head = runCatching {
            val parsed = Uri.parse(url)
            (parsed.scheme ?: "") + "://" + (parsed.host ?: "")
        }.getOrDefault("")
        return if (head.isBlank()) "\u2022\u2022\u2022\u2022\u2022\u2022$tail"
        else "$head/\u2026\u2022\u2022\u2022\u2022$tail"
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_BADGE_ID = "com.lanteng.star.app.extra.BADGE_ID"
    }
}
