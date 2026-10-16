package com.lanteng.star.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lanteng.star.app.R
import com.lanteng.star.app.backup.Backup
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.launch.BadgeLauncher
import com.lanteng.star.app.qr.QrCodec
import com.lanteng.star.app.util.Prefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: BadgeAdapter

    private val handler = Handler(Looper.getMainLooper())

    /** Refreshes the cooldown countdowns while the list is on screen. */
    private val ticker = object : Runnable {
        override fun run() {
            if (Store.badges.any { !it.badgeUrl.isNullOrBlank() }) {
                adapter.notifyDataSetChanged()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private lateinit var accountSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var emptyHint: TextView

    /** index 0 means "no account filter"; the rest map 1:1 onto this list. */
    private val accountIds = mutableListOf<String?>()
    private var categoryValues: List<String> = emptyList()

    private var accountFilter: String? = null
    private var categoryFilter: String? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) return@registerForActivityResult
        val badge = QrCodec.decode(contents)
        if (badge == null) {
            toast(getString(R.string.qr_bad_payload))
        } else {
            // The scanned record gets a fresh local id and drops any tag
            // observation, so a shared code can never silently overwrite an
            // existing badge on this device.
            Store.upsert(badge.copy(id = Store.newId(), tag = null))
            refresh()
            toast(getString(R.string.toast_saved))
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        toast(getString(if (Backup.write(this, uri)) R.string.toast_exported else R.string.toast_import_failed))
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        if (Backup.read(this, uri)) {
            refresh()
            toast(getString(R.string.toast_imported))
        } else {
            toast(getString(R.string.toast_import_failed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        accountSpinner = findViewById(R.id.account_spinner)
        categorySpinner = findViewById(R.id.spinner_categories)
        emptyHint = findViewById(R.id.tv_empty_hint)

        adapter = BadgeAdapter(
            prefs = prefs,
            onClick = { badge ->
                startActivity(
                    Intent(this, BadgeDetailActivity::class.java)
                        .putExtra(BadgeDetailActivity.EXTRA_BADGE_ID, badge.id),
                )
            },
            onTrigger = { badge -> deliver(badge) },
        )
        val recycler = findViewById<RecyclerView>(R.id.list_badges)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupToolbar()
        setupFilters()

        findViewById<Button>(R.id.btn_manage_accounts).setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            BadgeEditDialog.show(this, null) { refresh() }
        }
        findViewById<FloatingActionButton>(R.id.fab_nfc).setOnClickListener {
            startActivity(Intent(this, NfcReadActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.fab_qr).setOnClickListener { launchQrScan() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.postDelayed(ticker, 1000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun deliver(badge: com.lanteng.star.app.data.Badge) {
        if (badge.badgeUrl.isNullOrBlank()) {
            toast(getString(R.string.trigger_no_link))
            return
        }
        if (BadgeLauncher.trigger(this, badge, prefs)) {
            refreshList()
        } else {
            toast(getString(R.string.trigger_failed))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ADD_BADGE, false)) {
            BadgeEditDialog.show(this, null) { refresh() }
        }
    }

    // ------------------------------------------------------------------ setup

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                R.id.action_usage -> {
                    startActivity(Intent(this, UsageActivity::class.java)); true
                }
                R.id.action_export -> {
                    val name = intent.getStringExtra(EXTRA_EXPORT_NAME) ?: DEFAULT_BACKUP_NAME
                    exportLauncher.launch(name); true
                }
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/json", "text/plain")); true
                }
                else -> false
            }
        }
    }

    private fun setupFilters() {
        accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = accountIds.getOrNull(position)
                if (selected != accountFilter) {
                    accountFilter = selected
                    refreshList()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = if (position <= 0) null else categoryValues.getOrNull(position - 1)
                if (selected != categoryFilter) {
                    categoryFilter = selected
                    refreshList()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun launchQrScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.qr_scan_hint))
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    // ---------------------------------------------------------------- refresh

    private fun refresh() {
        refreshFilters()
        refreshList()
    }

    private fun refreshFilters() {
        // If the selected account vanished, fall back to "all".
        if (accountFilter != null && Store.accounts.none { it.id == accountFilter }) {
            accountFilter = null
        }

        accountIds.clear()
        val accountLabels = mutableListOf(getString(R.string.spinner_account_all))
        accountIds.add(null)
        Store.accounts.forEach {
            accountIds.add(it.id)
            accountLabels.add(it.name)
        }
        accountSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            accountLabels,
        )
        accountSpinner.setSelection(accountIds.indexOf(accountFilter).coerceAtLeast(0))

        categoryValues = Store.categories
        val categoryLabels = mutableListOf(getString(R.string.spinner_category_all))
        categoryLabels.addAll(categoryValues)
        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categoryLabels,
        )
        if (categoryFilter != null && !categoryValues.contains(categoryFilter)) {
            categoryFilter = null
        }
        categorySpinner.setSelection(
            categoryFilter?.let { categoryValues.indexOf(it) + 1 } ?: 0,
        )
    }

    private fun refreshList() {
        val list = Store.filtered(accountFilter, categoryFilter)
        adapter.submit(list)
        emptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_ADD_BADGE = "com.lanteng.star.app.extra.ADD_BADGE"
        private const val EXTRA_EXPORT_NAME = "com.lanteng.star.app.extra.EXPORT_NAME"
        private const val DEFAULT_BACKUP_NAME = "badge-collection-backup.json"
    }
}
