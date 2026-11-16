package com.socialvault.app

import com.socialvault.app.data.EmailAccount
import com.socialvault.app.data.EmailWithIds
import com.socialvault.app.data.SocialId
import com.socialvault.app.data.SocialIdWithPages
import com.socialvault.app.ui.filterEmails
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the exact list logic the vault home screen uses.
 * The reported bug: with the "Social" filter on, saved Apps/Websites
 * vanished from the list even though the stat chips counted them.
 */
class FilterTest {

    private fun entry(
        id: Long,
        emailId: Long,
        kind: String,
        platform: String = "",
        username: String = "",
        appName: String = "",
        siteName: String = "",
        createdAt: Long = 0
    ) = SocialIdWithPages(
        SocialId(
            id = id,
            emailAccountId = emailId,
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

    private fun email(id: Long, address: String, vararg ids: SocialIdWithPages) =
        EmailWithIds(EmailAccount(id = id, email = address), ids.toList())

    private val data = listOf(
        email(
            1, "ali@gmail.com",
            entry(1, 1, "SOCIAL", platform = "Facebook", username = "ali.fb", createdAt = 100),
            entry(2, 1, "APP", username = "ali", appName = "WhatsApp", createdAt = 300),
            entry(3, 1, "WEBSITE", username = "ali", siteName = "Daraz", createdAt = 200)
        ),
        email(
            2, "shop@gmail.com",
            entry(4, 2, "SOCIAL", platform = "Instagram", username = "shop.ig", createdAt = 400)
        )
    )

    @Test
    fun allShowsEverything() {
        val out = filterEmails(data, "", "All", false)
        assertEquals(2, out.size)
        assertEquals(3, out[0].ids.size)
        assertEquals(1, out[1].ids.size)
    }

    @Test
    fun socialFilterHidesAppsAndWebsites() {
        // This is what the user saw: only the Facebook profile, even though
        // the Apps/Websites were saved and counted in the stat chips.
        val out = filterEmails(data, "", "SOCIAL", false)
        assertEquals(2, out.size)
        assertEquals(listOf("SOCIAL"), out[0].ids.map { it.socialId.kind })
        assertEquals(listOf("SOCIAL"), out[1].ids.map { it.socialId.kind })
    }

    @Test
    fun appFilterShowsOnlyApps() {
        val out = filterEmails(data, "", "APP", false)
        assertEquals(1, out.size)
        assertEquals("ali@gmail.com", out[0].account.email)
        assertEquals(listOf("APP"), out[0].ids.map { it.socialId.kind })
    }

    @Test
    fun websiteFilterShowsOnlyWebsites() {
        val out = filterEmails(data, "", "WEBSITE", false)
        assertEquals(1, out.size)
        assertEquals(listOf("WEBSITE"), out[0].ids.map { it.socialId.kind })
    }

    @Test
    fun searchMatchesAppAndSiteNames() {
        assertEquals(1, filterEmails(data, "whatsapp", "All", false).size)
        assertEquals(1, filterEmails(data, "daraz", "All", false).size)
        assertEquals(0, filterEmails(data, "zzz-no-match", "All", false).size)
    }

    @Test
    fun sortNewestOrdersByLatestEntry() {
        val out = filterEmails(data, "", "All", true)
        assertEquals("shop@gmail.com", out[0].account.email) // latest entry 400
        assertEquals("ali@gmail.com", out[1].account.email) // latest entry 300
    }

    @Test
    fun alphabeticalSortByEmail() {
        val out = filterEmails(data, "", "All", false)
        assertEquals("ali@gmail.com", out[0].account.email)
        assertEquals("shop@gmail.com", out[1].account.email)
    }
}
