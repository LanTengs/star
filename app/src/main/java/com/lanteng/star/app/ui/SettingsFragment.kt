package com.lanteng.star.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lanteng.star.app.R
import com.lanteng.star.app.backup.Backup
import com.lanteng.star.app.floating.FloatingService
import com.lanteng.star.app.floating.IconPreference
import com.lanteng.star.app.launch.BadgeLauncher
import com.lanteng.star.app.util.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: Prefs

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        toast(if (Backup.write(requireContext(), uri)) R.string.toast_exported else R.string.toast_import_failed)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        toast(if (Backup.read(requireContext(), uri)) R.string.toast_imported else R.string.toast_import_failed)
    }

    private val iconLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.customIconUri = uri.toString()
        findPreference<IconPreference>(Prefs.KEY_CUSTOM_ICON)?.store(uri)
        FloatingService.refreshIcon(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        prefs = Prefs(requireContext())

        // ---- language: applied through the per-app locale API, stored locally
        findPreference<ListPreference>(Prefs.KEY_LANGUAGE)?.let { pref ->
            pref.value = prefs.language
            pref.setOnPreferenceChangeListener { _, newValue ->
                val tag = newValue as String
                prefs.language = tag
                val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(tag)
                AppCompatDelegate.setApplicationLocales(locales)
                true
            }
        }

        // ---- delivery / cooldown
        findPreference<EditTextPreference>(Prefs.KEY_EFFECT_MINUTES)?.let { pref ->
            pref.summary = getString(R.string.effect_minutes_summary)
            pref.setOnPreferenceChangeListener { _, newValue ->
                minutesOrNull(newValue)?.let { prefs.effectMinutes = it } != null
            }
        }
        findPreference<EditTextPreference>(Prefs.KEY_COOLDOWN_MINUTES)?.let { pref ->
            pref.summary = getString(R.string.cooldown_minutes_summary)
            pref.setOnPreferenceChangeListener { _, newValue ->
                minutesOrNull(newValue)?.let { prefs.cooldownMinutes = it } != null
            }
        }
        findPreference<Preference>(Prefs.KEY_TARGET_PACKAGE)?.let { pref ->
            renderTargetSummary(pref)
            pref.setOnPreferenceClickListener {
                showTargetPicker(pref)
                true
            }
        }

        // ---- floating panel service
        findPreference<SwitchPreferenceCompat>(Prefs.KEY_FOREGROUND)?.let { pref ->
            pref.isChecked = prefs.foregroundService
            pref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                prefs.foregroundService = enabled
                if (enabled) {
                    if (!FloatingService.canDrawOverlays(requireContext())) {
                        toast(R.string.toast_overlay_denied)
                    } else {
                        FloatingService.start(requireContext())
                    }
                } else {
                    FloatingService.stop(requireContext())
                }
                true
            }
        }

        findPreference<IconPreference>(Prefs.KEY_CUSTOM_ICON)?.let { pref ->
            pref.pickerLauncher = iconLauncher
            pref.store(prefs.customIconUri.takeIf { it.isNotEmpty() }?.let(Uri::parse))
        }

        // ---- background behaviour
        findPreference<Preference>("battery_whitelist")?.setOnPreferenceClickListener {
            requestBatteryExemption()
            true
        }

        // ---- data
        findPreference<Preference>("badge_usage_analysis")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), UsageActivity::class.java))
            true
        }
        findPreference<Preference>("data_export")?.setOnPreferenceClickListener {
            exportLauncher.launch("badge-collection-backup.json")
            true
        }
        findPreference<Preference>("data_import")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
            true
        }
        findPreference<Preference>("about")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
    }

    /** Accepts 1..600; anything else shows a message and keeps the old value. */
    private fun minutesOrNull(raw: Any?): Int? {
        val minutes = (raw as? String)?.trim()?.toIntOrNull()
        if (minutes == null || minutes < 1 || minutes > 600) {
            toast(R.string.number_invalid)
            return null
        }
        return minutes
    }

    private fun renderTargetSummary(pref: Preference) {
        val pkg = prefs.targetPackage
        pref.summary = if (pkg.isEmpty()) {
            getString(R.string.target_system)
        } else {
            BadgeLauncher.candidates(requireContext()).firstOrNull { it.first == pkg }?.second
                ?: pkg
        }
    }

    private fun showTargetPicker(pref: Preference) {
        val candidates = BadgeLauncher.candidates(requireContext())
        val labels = mutableListOf(getString(R.string.target_system))
        val packages = mutableListOf("")
        candidates.forEach { (pkg, label) ->
            packages.add(pkg)
            labels.add(label)
        }
        val current = packages.indexOf(prefs.targetPackage).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.target_package_title)
            .setSingleChoiceItems(labels.toTypedArray(), current) { dialog, which ->
                prefs.targetPackage = packages[which]
                renderTargetSummary(pref)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun requestBatteryExemption() {        val packageUri = Uri.parse("package:" + requireContext().packageName)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { toast(R.string.toast_overlay_denied) }
            }
    }

    private fun toast(resId: Int) =
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
}
