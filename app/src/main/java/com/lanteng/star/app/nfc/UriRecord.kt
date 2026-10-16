package com.lanteng.star.app.nfc

import android.nfc.NdefRecord

/**
 * Decodes a well-known URI record (RTD-URI) into its text form.
 *
 * The NFC Forum URI record stores a one-byte prefix code plus the remaining
 * UTF-8 text, so the full link has to be reassembled here.
 */
object UriRecord {

    fun parse(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return null
        val type = record.type ?: return null
        if (!type.contentEquals(NdefRecord.RTD_URI)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null

        val prefix = PREFIXES.getOrNull(payload[0].toInt() and 0xFF) ?: return null
        val rest = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        return prefix + rest
    }

    /** The NFC Forum URI prefix table (abbreviated codes 0x00..0x23). */
    private val PREFIXES = arrayOf(
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:",
    )
}
