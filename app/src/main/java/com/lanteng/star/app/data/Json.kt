package com.lanteng.star.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * JSON mapping for the collection document, shared by the store and by the QR /
 * backup coders so there is exactly one definition of the on-disk shape.
 */

internal fun accountToJson(account: Account): JSONObject = JSONObject().apply {
    put("id", account.id)
    put("name", account.name)
}

internal fun accountFromJson(o: JSONObject): Account =
    Account(o.optString("id", UUID.randomUUID().toString()), o.optString("name", ""))

internal fun tagToJson(tag: TagInfo): JSONObject = JSONObject().apply {
    put("uid", tag.uid)
    put("tech", JSONArray().apply { tag.techList.forEach { put(it) } })
    put("ndef", tag.ndef ?: JSONObject.NULL)
    put("pages", tag.pages ?: JSONObject.NULL)
    put("readAt", tag.readAt)
}

internal fun tagFromJson(o: JSONObject): TagInfo {
    val tech = mutableListOf<String>()
    o.optJSONArray("tech")?.let { arr ->
        for (i in 0 until arr.length()) tech.add(arr.getString(i))
    }
    return TagInfo(
        uid = o.optString("uid", ""),
        techList = tech,
        ndef = if (o.isNull("ndef")) null else o.optString("ndef"),
        pages = if (o.isNull("pages")) null else o.optString("pages"),
        readAt = o.optLong("readAt", 0L),
    )
}

internal fun badgeToJson(badge: Badge): JSONObject = JSONObject().apply {
    put("id", badge.id)
    put("name", badge.name)
    put("category", badge.category)
    put("accountId", badge.accountId)
    put("acquiredAt", badge.acquiredAt ?: JSONObject.NULL)
    put("notes", badge.notes)
    put("imageUri", badge.imageUri ?: JSONObject.NULL)
    put("tag", badge.tag?.let { tagToJson(it) } ?: JSONObject.NULL)
    put("badgeUrl", badge.badgeUrl ?: JSONObject.NULL)
    put("lastUsedAt", badge.lastUsedAt ?: JSONObject.NULL)
}

/**
 * Record shape used by the QR round trip: everything except the badge link.
 * A shared code must never be a way to move a badge link between people.
 */
internal fun badgeToShareableJson(badge: Badge): JSONObject =
    badgeToJson(badge).apply {
        remove("badgeUrl")
        remove("lastUsedAt")
        remove("tag")
    }

internal fun badgeFromJson(o: JSONObject): Badge = Badge(
    id = o.optString("id", UUID.randomUUID().toString()),
    name = o.optString("name", ""),
    category = o.optString("category", ""),
    accountId = o.optString("accountId", ""),
    acquiredAt = if (o.isNull("acquiredAt")) null else o.optLong("acquiredAt"),
    notes = o.optString("notes", ""),
    imageUri = if (o.isNull("imageUri")) null else o.optString("imageUri"),
    tag = if (o.isNull("tag")) null else tagFromJson(o.getJSONObject("tag")),
    badgeUrl = if (o.isNull("badgeUrl")) null else o.optString("badgeUrl"),
    lastUsedAt = if (o.isNull("lastUsedAt")) null else o.optLong("lastUsedAt"),
)
