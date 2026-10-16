package com.lanteng.star.app.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.lanteng.star.app.data.TagInfo
import java.io.ByteArrayOutputStream

/**
 * Reads an NFC tag and describes it.
 *
 * READ-ONLY BY DESIGN. The only command this class ever sends is the NFC Forum
 * Type 2 `READ` command (0x30). There is intentionally no `WRITE` (0xA2), no
 * `PWD_AUTH`, no lock or format command, and no host-card-emulation code
 * anywhere in this app.
 */
object TagReader {

    private const val CMD_READ = 0x30
    private const val MAX_PAGES = 64
    private const val PAGE_SIZE = 4

    fun read(tag: Tag): TagInfo {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        val techList = tag.techList
            .map { it.substringAfterLast('.') }
            .filter { it.isNotBlank() }
            .sorted()

        return TagInfo(
            uid = uid,
            techList = techList,
            ndef = readNdef(tag),
            pages = dumpPages(tag),
            readAt = System.currentTimeMillis(),
        )
    }

    /** Summarises any NDEF message the tag already carries. */
    private fun readNdef(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val message: NdefMessage = runCatching { ndef.cachedNdefMessage }.getOrNull() ?: return null
        return describe(message)
    }

    /**
     * The link carried by a URI record, which is what a badge card holds.
     *
     * Reading it is the one and only way a badge gets a link in this app: it must
     * come off the user's own physical card. Nothing else supplies one.
     */
    fun firstUri(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val message: NdefMessage = runCatching { ndef.cachedNdefMessage }.getOrNull() ?: return null
        for (record in message.records) {
            UriRecord.parse(record)?.let { return it }
        }
        return null
    }

    /**
     * Structure only: record count, TNF, type and payload length. Payload bytes
     * are deliberately not echoed here -- the link itself is kept exactly once,
     * in the badge record, instead of being duplicated as a hex blob.
     */
    private fun describe(message: NdefMessage): String = buildString {
        message.records.forEachIndexed { index, record ->
            if (index > 0) append('\n')
            append("record ").append(index).append(": tnf=").append(record.tnf)
            val type = record.type?.toString(Charsets.US_ASCII)
            if (!type.isNullOrEmpty()) append(" type=").append(type)
            append(" payload=").append(record.payload?.size ?: 0).append('B')
        }
    }

    /**
     * Best-effort hex dump of the tag's user memory pages.
     *
     * An unreadable tag is still a useful observation (UID + tech list), so any
     * failure here must not lose the read: every error is swallowed and the
     * method simply reports "no dump".
     */
    private fun dumpPages(tag: Tag): String? {
        val bytes = readType2Pages(tag) ?: return null
        if (bytes.isEmpty()) return null

        return buildString {
            var page = 0
            while (page * PAGE_SIZE < bytes.size) {
                val start = page * PAGE_SIZE
                val end = minOf(start + PAGE_SIZE, bytes.size)
                append("%02X: ".format(page))
                for (i in start until end) append("%02X ".format(bytes[i]))
                append('\n')
                page++
            }
        }.trimEnd()
    }

    /** NFC Forum Type 2 (NfcA / NTAG family) only. */
    private fun readType2Pages(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag) ?: return null
        return try {
            nfcA.connect()
            val out = ByteArrayOutputStream()
            for (page in 0 until MAX_PAGES) {
                val response = nfcA.transceive(byteArrayOf(CMD_READ.toByte(), page.toByte()))
                if (response == null || response.size < PAGE_SIZE) break
                out.write(response, 0, PAGE_SIZE)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { nfcA.close() }
        }
    }

    /** True when the tag is a family we can say something useful about. */
    fun isSupportedFamily(tag: Tag): Boolean =
        NfcA.get(tag) != null || NfcB.get(tag) != null ||
            NfcF.get(tag) != null || NfcV.get(tag) != null
}
