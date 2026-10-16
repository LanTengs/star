package com.lanteng.star.app.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.lanteng.star.app.data.Badge
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.util.Prefs

/**
 * Hands the badge link the user already owns back to the system, so the game can
 * pick it up without physically tapping the card again.
 *
 * This is deliberately the whole mechanism: build an `ACTION_VIEW` intent for the
 * link that is already stored on this device and let the OS/game handle it.
 * Nothing here fabricates, derives or imports a badge link -- a badge only gets
 * one by being read off the user's own card.
 */
object BadgeLauncher {

    /** Milliseconds left before the badge can be used again. */
    fun cooldownRemainingMs(badge: Badge, prefs: Prefs): Long {
        val last = badge.lastUsedAt ?: return 0L
        val total = prefs.cooldownMinutes * 60_000L
        return (total - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
    }

    /** Milliseconds left of the active effect, if any. */
    fun effectRemainingMs(badge: Badge, prefs: Prefs): Long {
        val last = badge.lastUsedAt ?: return 0L
        val total = prefs.effectMinutes * 60_000L
        return (total - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
    }

    fun isReady(badge: Badge, prefs: Prefs): Boolean =
        badge.badgeUrl.isNullOrBlank() || cooldownRemainingMs(badge, prefs) == 0L

    /**
     * Open the stored link. Returns false when there is no link or when nothing
     * on the device can handle it.
     */
    fun trigger(context: Context, badge: Badge, prefs: Prefs): Boolean {
        val raw = badge.badgeUrl
        if (raw.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false

        val preferred = prefs.targetPackage
        if (preferred.isNotEmpty()) {
            val direct = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(preferred)
            if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) {
                markUsed(badge)
                return true
            }
        }

        val open = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(open); true }.getOrDefault(false)
        if (ok) markUsed(badge)
        return ok
    }

    private fun markUsed(badge: Badge) {
        badge.lastUsedAt = System.currentTimeMillis()
        Store.upsert(badge)
    }

    /** Apps that can open an http(s) link, for the "open with" preference. */
    fun candidates(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid"))
        val resolved = runCatching {
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())

        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching {
                    info.loadLabel(pm).toString()
                }.getOrDefault(pkg)
                pkg to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    /** "12:34" style countdown. */
    fun format(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
