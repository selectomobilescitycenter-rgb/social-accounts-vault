package com.socialvault.app.ui

import com.socialvault.app.data.EmailWithIds
import com.socialvault.app.data.SocialIdWithPages

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
    sortNewest: Boolean,
    platformFilter: String? = null
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
            val platformOk = platformFilter == null || s.platform == platformFilter
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
            kindOk && platformOk && searchOk
        }
        if (ids.isEmpty()) null else e.copy(ids = ids)
    }
}

/** One group on the panel's first page: all IDs of one kind/platform. */
data class EntrySection(val title: String, val icon: String, val entries: List<SocialIdWithPages>)

private val KNOWN_SOCIAL_ORDER = listOf("Facebook", "Instagram", "TikTok", "YouTube")

/**
 * Group an email's entries for the panel's first page: Apps, then Websites,
 * then one section per social platform — never mixed in one list. Newest
 * first inside each section. Unit-tested (GroupTest).
 */
fun groupEntries(
    ids: List<SocialIdWithPages>,
    kindFilter: String = "All",
    platformFilter: String? = null
): List<EntrySection> {
    // Honour the home screen's filter first: with "Apps" active only apps
    // reach the panel, with "Facebook" only Facebook.
    val visible = ids.filter { idw ->
        val s = idw.socialId
        (kindFilter == "All" || s.kind == kindFilter) &&
            (platformFilter == null || s.platform == platformFilter)
    }
    val newestFirst = { list: List<SocialIdWithPages> ->
        list.sortedByDescending { it.socialId.createdAt }
    }
    val sections = mutableListOf<EntrySection>()
    val apps = visible.filter { it.socialId.kind == "APP" }
    if (apps.isNotEmpty()) sections += EntrySection("Apps", "📱", newestFirst(apps))
    val sites = visible.filter { it.socialId.kind == "WEBSITE" }
    if (sites.isNotEmpty()) sections += EntrySection("Websites", "🌐", newestFirst(sites))
    val social = visible.filter { it.socialId.kind == "SOCIAL" }
        .groupBy { it.socialId.platform.ifBlank { "Social" } }
    val ordered = KNOWN_SOCIAL_ORDER.filter { it in social } +
        (social.keys - KNOWN_SOCIAL_ORDER.toSet()).sorted()
    for (platform in ordered) {
        sections += EntrySection(platform, platformIcon(platform), newestFirst(social.getValue(platform)))
    }
    val other = visible.filter {
        it.socialId.kind != "APP" && it.socialId.kind != "WEBSITE" && it.socialId.kind != "SOCIAL"
    }
    if (other.isNotEmpty()) sections += EntrySection("Other", "🔐", newestFirst(other))
    return sections
}
