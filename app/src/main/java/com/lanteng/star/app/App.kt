package com.lanteng.star.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.util.Prefs

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        applyLanguage()
    }

    /**
     * Language is a local preference only -- there is no remote string source.
     * "system" hands control back to the platform.
     */
    private fun applyLanguage() {
        val tag = Prefs(this).language
        val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
