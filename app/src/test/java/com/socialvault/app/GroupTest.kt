package com.socialvault.app

import com.socialvault.app.data.SocialId
import com.socialvault.app.data.SocialIdWithPages
import com.socialvault.app.ui.groupEntries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the panel's first page: saved IDs grouped by kind/platform,
 * never mixed in one list. The user reported apps and IDs all mixed
 * together in one place — this is the exact behaviour the redesign fixes.
 */
class GroupTest {

    private fun entry(
        id: Long,
        kind: String,
        platform: String = "",
        username: String = "",
        appName: String = "",
        siteName: String = "",
        createdAt: Long = 0
    ) = SocialIdWithPages(
        SocialId(
            id = id,
            emailAccountId = 1,
            platform = platform,
            username = username,
            country = "Pakistan",
            password = "pw$id",
            kind = kind,
            siteName = siteName,
            appName = appName,
            createdAt = createdAt
        )
    )

    @Test
    fun groupsByKindAndPlatform() {
        val ids = listOf(
            entry(1, "SOCIAL", platform = "Facebook", username = "fb1", createdAt = 100),
            entry(2, "APP", username = "u", appName = "WhatsApp", createdAt = 300),
            entry(3, "WEBSITE", username = "u", siteName = "Daraz", createdAt = 200),
            entry(4, "SOCIAL", platform = "Facebook", username = "fb2", createdAt = 400),
            entry(5, "SOCIAL", platform = "YouTube", username = "yt1", createdAt = 500)
        )
        val sections = groupEntries(ids)
        assertEquals(listOf("Apps", "Websites", "Facebook", "YouTube"), sections.map { it.title })
        assertEquals(1, sections[0].entries.size) // Apps
        assertEquals(1, sections[1].entries.size) // Websites
        assertEquals(2, sections[2].entries.size) // Facebook
        assertEquals(1, sections[3].entries.size) // YouTube
    }

    @Test
    fun newestFirstInsideSection() {
        val ids = listOf(
            entry(1, "SOCIAL", platform = "Facebook", username = "old", createdAt = 100),
            entry(2, "SOCIAL", platform = "Facebook", username = "new", createdAt = 900)
        )
        val sections = groupEntries(ids)
        assertEquals("new", sections[0].entries[0].socialId.username)
        assertEquals("old", sections[0].entries[1].socialId.username)
    }

    @Test
    fun emptySectionsAreSkipped() {
        val sections = groupEntries(listOf(entry(1, "APP", username = "u", appName = "JazzCash")))
        assertEquals(listOf("Apps"), sections.map { it.title })
        assertEquals(emptyList<String>(), groupEntries(emptyList()).map { it.title })
    }

    @Test
    fun unknownPlatformGetsOwnSectionAfterKnownOnes() {
        val ids = listOf(
            entry(1, "SOCIAL", platform = "Snapchat", username = "s", createdAt = 100),
            entry(2, "SOCIAL", platform = "Facebook", username = "f", createdAt = 100)
        )
        assertEquals(listOf("Facebook", "Snapchat"), groupEntries(ids).map { it.title })
    }
}
