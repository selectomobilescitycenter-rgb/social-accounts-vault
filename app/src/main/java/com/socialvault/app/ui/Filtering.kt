package com.socialvault.app.ui

import com.socialvault.app.data.EmailWithIds

/**
 * Pure list logic for the vault home screen: sort + kind filter + search.
 * Kept free of Android/Compose so unit tests can exercise the exact code
 * the screen uses (see FilterTest).
 *
 * kindFilter: "All" | "SOCIAL" | "WEBSITE" | "APP".
 * An email is kept only when at least one of its entries matches; the
 * email is returned with ONLY the matching entries, so the card and the
 * detail panel never show entries the filter hid.
 */
fun filterEmails(
    emails: List<EmailWithIds>,
    query: String,
    kindFilter: String,
    sortNewest: Boolean
): List<EmailWithIds> {
    val sorted = if (sortNewest) {
        emails.sortedByDescending { e ->
            e.ids.maxOfOrNull { it.socialId.createdAt } ?: 0L
        }
    } else {
        emails.sortedBy { it.account.email.lowercase() }
    }
    val q = query.trim()
    return sorted.mapNotNull { e ->
        val ids = e.ids.filter { idw ->
            val s = idw.socialId
            val kindOk = kindFilter == "All" || s.kind == kindFilter
            val searchOk = q.isBlank() ||
                e.account.email.contains(q, ignoreCase = true) ||
                s.username.contains(q, ignoreCase = true) ||
                s.platform.contains(q, ignoreCase = true) ||
                s.country.contains(q, ignoreCase = true) ||
                s.siteName.contains(q, ignoreCase = true) ||
                s.url.contains(q, ignoreCase = true) ||
                s.appName.contains(q, ignoreCase = true) ||
                s.notes.contains(q, ignoreCase = true) ||
                s.category.contains(q, ignoreCase = true)
            kindOk && searchOk
        }
        if (ids.isEmpty()) null else e.copy(ids = ids)
    }
}
