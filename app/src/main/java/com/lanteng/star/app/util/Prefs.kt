package com.lanteng.star.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Thin typed wrapper over the default SharedPreferences used by preferences.xml.
 *
 * Every value here is local. There is no account token, no endpoint and no
 * sync cursor, because the app has no network stack.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var switchCard: Boolean
        get() = sp.getBoolean(KEY_SWITCH_CARD, false)
        set(v) = sp.edit().putBoolean(KEY_SWITCH_CARD, v).apply()

    var dialogBlur: Boolean
        get() = sp.getBoolean(KEY_DIALOG_BLUR, true)
        set(v) = sp.edit().putBoolean(KEY_DIALOG_BLUR, v).apply()

    var autoHidePanel: Boolean
        get() = sp.getBoolean(KEY_AUTO_HIDE, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_HIDE, v).apply()

    var clickOutsideToHide: Boolean
        get() = sp.getBoolean(KEY_CLICK_OUTSIDE, false)
        set(v) = sp.edit().putBoolean(KEY_CLICK_OUTSIDE, v).apply()

    var iconOnlyPanel: Boolean
        get() = sp.getBoolean(KEY_ICON_ONLY, false)
        set(v) = sp.edit().putBoolean(KEY_ICON_ONLY, v).apply()

    var volumeKeysToggle: Boolean
        get() = sp.getBoolean(KEY_VOLUME_TOGGLE, false)
        set(v) = sp.edit().putBoolean(KEY_VOLUME_TOGGLE, v).apply()

    var shakeToggle: Boolean
        get() = sp.getBoolean(KEY_SHAKE_TOGGLE, false)
        set(v) = sp.edit().putBoolean(KEY_SHAKE_TOGGLE, v).apply()

    var customIconUri: String
        get() = sp.getString(KEY_CUSTOM_ICON, "") ?: ""
        set(v) = sp.edit().putString(KEY_CUSTOM_ICON, v).apply()

    var foregroundService: Boolean
        get() = sp.getBoolean(KEY_FOREGROUND, false)
        set(v) = sp.edit().putBoolean(KEY_FOREGROUND, v).apply()

    var bootStartup: Boolean
        get() = sp.getBoolean(KEY_BOOT, false)
        set(v) = sp.edit().putBoolean(KEY_BOOT, v).apply()

    var hideRecentTasks: Boolean
        get() = sp.getBoolean(KEY_HIDE_RECENTS, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE_RECENTS, v).apply()

    var lockRecentTasks: Boolean
        get() = sp.getBoolean(KEY_LOCK_RECENTS, false)
        set(v) = sp.edit().putBoolean(KEY_LOCK_RECENTS, v).apply()

    var language: String
        get() = sp.getString(KEY_LANGUAGE, "system") ?: "system"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    /** How long a badge effect lasts once delivered, in minutes. */
    var effectMinutes: Int
        get() = sp.getString(KEY_EFFECT_MINUTES, "10")?.toIntOrNull() ?: 10
        set(v) = sp.edit().putString(KEY_EFFECT_MINUTES, v.toString()).apply()

    /** How long before the same badge can be delivered again, in minutes. */
    var cooldownMinutes: Int
        get() = sp.getString(KEY_COOLDOWN_MINUTES, "15")?.toIntOrNull() ?: 15
        set(v) = sp.edit().putString(KEY_COOLDOWN_MINUTES, v.toString()).apply()

    /** Optional package to hand badge links to; empty means "let the system choose". */
    var targetPackage: String
        get() = sp.getString(KEY_TARGET_PACKAGE, "") ?: ""
        set(v) = sp.edit().putString(KEY_TARGET_PACKAGE, v).apply()

    /** Extra translations the user imported from a local JSON file. */
    var importedStringsJson: String
        get() = sp.getString(KEY_IMPORTED_STRINGS, "") ?: ""
        set(v) = sp.edit().putString(KEY_IMPORTED_STRINGS, v).apply()

    companion object {
        const val KEY_SWITCH_CARD = "switch_card"
        const val KEY_DIALOG_BLUR = "dialog_blur_enabled"
        const val KEY_AUTO_HIDE = "auto_hide_floating_panel"
        const val KEY_CLICK_OUTSIDE = "click_outside_to_hide"
        const val KEY_ICON_ONLY = "icon_only_floating_panel"
        const val KEY_VOLUME_TOGGLE = "volume_keys_toggle_floating"
        const val KEY_SHAKE_TOGGLE = "shake_toggle_floating"
        const val KEY_CUSTOM_ICON = "custom_floating_icon"
        const val KEY_FOREGROUND = "foreground_service"
        const val KEY_BOOT = "boot_startup"
        const val KEY_HIDE_RECENTS = "hide_recent_tasks"
        const val KEY_LOCK_RECENTS = "lock_recent_tasks"
        const val KEY_LANGUAGE = "language_selection"
        const val KEY_EFFECT_MINUTES = "effect_minutes"
        const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_IMPORTED_STRINGS = "imported_strings_json"
    }
}
