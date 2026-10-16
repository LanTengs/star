package com.lanteng.star.app.floating

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import androidx.activity.result.ActivityResultLauncher
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lanteng.star.app.R

/**
 * Preference that stores a locally chosen image for the floating button.
 *
 * The URI comes from the Storage Access Framework with a persisted read grant,
 * so no storage permission is required and nothing is copied off the device.
 */
class IconPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    var pickerLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        summary = currentSummary()
    }

    override fun onClick() {
        pickerLauncher?.launch(arrayOf("image/*"))
    }

    fun store(uri: Uri?) {
        val value = uri?.toString().orEmpty()
        persistString(value)
        summary = currentSummary()
    }

    private fun currentSummary(): CharSequence {
        val stored = getPersistedString("")
        return if (stored.isEmpty()) context.getString(R.string.custom_floating_icon_summary)
        else Uri.parse(stored).lastPathSegment
            ?: context.getString(R.string.custom_floating_icon_summary)
    }
}
