package com.lanteng.star.app.data

import android.content.Context
import com.lanteng.star.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The whole collection lives in one JSON document inside the app's private
 * files directory.
 *
 * Chosen deliberately over a database: the data set is small (tens to a few
 * hundred records), it makes the local backup/restore path exact, and it means
 * the app has no dependency that could ever want a network.
 */
object Store {

    private const val FILE_NAME = "collection.json"
    private const val VERSION = 1

    private var file: File? = null

    val accounts = mutableListOf<Account>()
    val badges = mutableListOf<Badge>()

    /**
     * Seed categories, used only to pre-populate the editor.
     *
     * Read from resources rather than written as literals here: no non-ASCII
     * text belongs in the sources, and the names stay translatable.
     */
    var defaultCategories: List<String> = emptyList()
        private set

    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        defaultCategories =
            context.resources.getStringArray(R.array.default_categories).toList()
        load()
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- queries

    val categories: List<String>
        get() {
            val set = LinkedHashSet<String>()
            set.addAll(defaultCategories)
            badges.forEach { if (it.category.isNotBlank()) set.add(it.category) }
            return set.sorted()
        }

    fun accountName(accountId: String): String =
        accounts.firstOrNull { it.id == accountId }?.name ?: ""

    fun badgeCountFor(accountId: String): Int = badges.count { it.accountId == accountId }

    fun findBadge(id: String): Badge? = badges.firstOrNull { it.id == id }

    fun filtered(accountId: String?, category: String?): List<Badge> =
        badges.filter { b ->
            (accountId == null || b.accountId == accountId) &&
                (category == null || b.category == category)
        }.sortedWith(compareByDescending<Badge> { it.acquiredAt ?: 0L }.thenBy { it.name })

    // --------------------------------------------------------------- mutation

    fun upsert(badge: Badge) {
        val index = badges.indexOfFirst { it.id == badge.id }
        if (index >= 0) badges[index] = badge else badges.add(badge)
        save()
    }

    fun deleteBadge(id: String) {
        badges.removeAll { it.id == id }
        save()
    }

    fun addAccount(name: String): Account {
        val account = Account(newId(), name)
        accounts.add(account)
        save()
        return account
    }

    fun renameAccount(id: String, name: String) {
        accounts.firstOrNull { it.id == id }?.let {
            it.name = name
            save()
        }
    }

    /** Refuses to drop an account that still owns badges. */
    fun deleteAccount(id: String): Boolean {
        if (badgeCountFor(id) > 0) return false
        accounts.removeAll { it.id == id }
        save()
        return true
    }

    // ------------------------------------------------------------ persistence

    fun load() {
        accounts.clear()
        badges.clear()
        val target = file ?: return
        if (!target.exists()) return
        try {
            val root = JSONObject(target.readText())
            root.optJSONArray("accounts")?.let { arr ->
                for (i in 0 until arr.length()) accounts.add(accountFromJson(arr.getJSONObject(i)))
            }
            root.optJSONArray("badges")?.let { arr ->
                for (i in 0 until arr.length()) badges.add(badgeFromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
            // A corrupt file must not brick the app: keep whatever parsed and
            // leave the bad file untouched so nothing is silently destroyed.
        }
    }

    fun save() {
        val target = file ?: return
        runCatching { target.writeText(snapshotJson()) }
    }

    fun snapshotJson(): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("accounts", JSONArray().apply { accounts.forEach { put(accountToJson(it)) } })
        root.put("badges", JSONArray().apply { badges.forEach { put(badgeToJson(it)) } })
        return root.toString(2)
    }

    /**
     * Replace everything from a backup document. Returns false and changes
     * nothing when the document is not one of ours.
     */
    fun restoreFromJson(text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (!root.has("badges") && !root.has("accounts")) return false

        val newAccounts = mutableListOf<Account>()
        root.optJSONArray("accounts")?.let { arr ->
            for (i in 0 until arr.length()) newAccounts.add(accountFromJson(arr.getJSONObject(i)))
        }
        val newBadges = mutableListOf<Badge>()
        root.optJSONArray("badges")?.let { arr ->
            for (i in 0 until arr.length()) newBadges.add(badgeFromJson(arr.getJSONObject(i)))
        }

        accounts.clear(); accounts.addAll(newAccounts)
        badges.clear(); badges.addAll(newBadges)
        save()
        return true
    }
}
