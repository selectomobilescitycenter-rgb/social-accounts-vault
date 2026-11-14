package com.socialvault.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup / restore as human-readable JSON (org.json is part of Android,
 * no extra dependency). The file contains ALL passwords in readable form —
 * the UI warns the user to keep it private and never share it.
 *
 * Format:
 * {
 *   "app": "Social Accounts Vault", "version": 2, "exportedAt": <epoch ms>,
 *   "emails": [ { "email": "...", "entries": [ {...} ] } ]
 * }
 */
data class BackupPage(val name: String, val followers: String)

data class BackupEntry(
    val kind: String,
    val platform: String,
    val username: String,
    val country: String,
    val password: String,
    val siteName: String,
    val url: String,
    val appName: String,
    val packageName: String,
    val category: String,
    val notes: String,
    val recoveryEmail: String,
    val recoveryPhone: String,
    val totpSecret: String,
    val createdAt: Long,
    val pages: List<BackupPage>
)

data class BackupEmail(val email: String, val entries: List<BackupEntry>)

data class BackupData(val emails: List<BackupEmail>)

fun buildBackupJson(emails: List<EmailWithIds>): String {
    val root = JSONObject()
    root.put("app", "Social Accounts Vault")
    root.put("version", 2)
    root.put("exportedAt", System.currentTimeMillis())
    val emailsJson = JSONArray()
    for (email in emails) {
        val emailJson = JSONObject()
        emailJson.put("email", email.account.email)
        val entriesJson = JSONArray()
        for (idw in email.ids) {
            val e = idw.socialId
            val entryJson = JSONObject()
            entryJson.put("kind", e.kind)
            entryJson.put("platform", e.platform)
            entryJson.put("username", e.username)
            entryJson.put("country", e.country)
            entryJson.put("password", e.password)
            entryJson.put("siteName", e.siteName)
            entryJson.put("url", e.url)
            entryJson.put("appName", e.appName)
            entryJson.put("packageName", e.packageName)
            entryJson.put("category", e.category)
            entryJson.put("notes", e.notes)
            entryJson.put("recoveryEmail", e.recoveryEmail)
            entryJson.put("recoveryPhone", e.recoveryPhone)
            entryJson.put("totpSecret", e.totpSecret)
            entryJson.put("createdAt", e.createdAt)
            val pagesJson = JSONArray()
            for (page in idw.pages) {
                val pageJson = JSONObject()
                pageJson.put("name", page.name)
                pageJson.put("followers", page.followers)
                pagesJson.put(pageJson)
            }
            entryJson.put("pages", pagesJson)
            entriesJson.put(entryJson)
        }
        emailJson.put("entries", entriesJson)
        emailsJson.put(emailJson)
    }
    root.put("emails", emailsJson)
    return root.toString(2)
}

/** Parse a backup file. Throws on invalid JSON or wrong format. */
fun parseBackupJson(json: String): BackupData {
    val root = JSONObject(json)
    if (root.optString("app") != "Social Accounts Vault") {
        throw IllegalArgumentException("Not a Social Accounts Vault backup file")
    }
    val emailsJson = root.getJSONArray("emails")
    val emails = ArrayList<BackupEmail>()
    for (i in 0 until emailsJson.length()) {
        val emailJson = emailsJson.getJSONObject(i)
        val entriesJson = emailJson.getJSONArray("entries")
        val entries = ArrayList<BackupEntry>()
        for (j in 0 until entriesJson.length()) {
            val e = entriesJson.getJSONObject(j)
            val pagesJson = e.optJSONArray("pages")
            val pages = ArrayList<BackupPage>()
            if (pagesJson != null) {
                for (k in 0 until pagesJson.length()) {
                    val p = pagesJson.getJSONObject(k)
                    pages.add(BackupPage(p.optString("name"), p.optString("followers")))
                }
            }
            entries.add(
                BackupEntry(
                    kind = e.optString("kind", "SOCIAL"),
                    platform = e.optString("platform"),
                    username = e.optString("username"),
                    country = e.optString("country"),
                    password = e.optString("password"),
                    siteName = e.optString("siteName"),
                    url = e.optString("url"),
                    appName = e.optString("appName"),
                    packageName = e.optString("packageName"),
                    category = e.optString("category", "Personal"),
                    notes = e.optString("notes"),
                    recoveryEmail = e.optString("recoveryEmail"),
                    recoveryPhone = e.optString("recoveryPhone"),
                    totpSecret = e.optString("totpSecret"),
                    createdAt = e.optLong("createdAt", 0L),
                    pages = pages
                )
            )
        }
        emails.add(BackupEmail(emailJson.getString("email"), entries))
    }
    return BackupData(emails)
}
