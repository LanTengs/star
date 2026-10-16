package com.lanteng.star.app.backup

import android.content.Context
import android.net.Uri
import com.lanteng.star.app.data.Store
import java.nio.charset.StandardCharsets

/**
 * Local file backup/restore through the Storage Access Framework.
 *
 * The user picks the destination; the app writes exactly the same JSON document
 * it keeps in its private directory. This is the offline replacement for the
 * original build's cloud re-sync entry: nothing leaves the device unless the
 * user explicitly hands the file to something else.
 */
object Backup {

    fun write(context: Context, uri: Uri): Boolean = runCatching {
        val bytes = Store.snapshotJson().toByteArray(StandardCharsets.UTF_8)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
        stream.use { it.write(bytes) }
        true
    }.getOrDefault(false)

    fun read(context: Context, uri: Uri): Boolean {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }.getOrNull() ?: return false
        return Store.restoreFromJson(text)
    }
}
