package com.lanteng.star.app.data

/** An account groups badges. Purely local; never synchronised anywhere. */
data class Account(
    val id: String,
    var name: String,
)

/**
 * Raw, read-only observation of one NFC tag. Recorded so the user can tell two
 * physical badges apart and attach notes to them.
 *
 * There is deliberately no field able to reproduce this payload back onto a tag:
 * the app never writes to or emulates an NFC tag.
 */
data class TagInfo(
    val uid: String,
    val techList: List<String>,
    val ndef: String?,
    val pages: String?,
    val readAt: Long,
)

/**
 * One badge in the collection.
 *
 * [badgeUrl] is the link the user's own physical badge card carries. It is kept
 * locally so the badge effect can be re-delivered without re-tapping a tag whose
 * sensing area is awkward. It is never uploaded, never exported and never
 * written back to another card.
 *
 * [lastUsedAt] drives the cooldown countdown (the effect lasts a few minutes,
 * then the badge cannot be used again for a while).
 */
data class Badge(
    val id: String,
    var name: String,
    var category: String,
    var accountId: String,
    var acquiredAt: Long?,
    var notes: String,
    var imageUri: String?,
    var tag: TagInfo?,
    var badgeUrl: String? = null,
    var lastUsedAt: Long? = null,
)
