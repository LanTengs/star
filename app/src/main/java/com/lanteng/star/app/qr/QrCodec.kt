package com.lanteng.star.app.qr

import com.lanteng.star.app.data.Badge
import com.lanteng.star.app.data.badgeFromJson
import com.lanteng.star.app.data.badgeToJson
import org.json.JSONObject

/**
 * Self-contained badge record, used for the QR round trip.
 *
 * The payload carries only the user's own record: a name, a category, a date and
 * notes. It deliberately does NOT carry anything read off an NFC tag, so a
 * shared code can never be used to reproduce a physical tag.
 */
object QrCodec {

    private const val MARKER = "starbadges.badge.v1"

    fun encode(badge: Badge): String = JSONObject().apply {
        put("app", MARKER)
        put("badge", badgeToJson(badge))
    }.toString()

    fun decode(text: String): Badge? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("app") != MARKER) return null
        val badge = root.optJSONObject("badge") ?: return null
        return runCatching { badgeFromJson(badge) }.getOrNull()
    }
}
