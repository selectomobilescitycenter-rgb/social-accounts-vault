package com.socialvault.app

import com.socialvault.app.data.AccountDao
import com.socialvault.app.data.EmailAccount
import com.socialvault.app.data.EmailWithIds
import com.socialvault.app.data.FacebookPage
import com.socialvault.app.data.SocialId
import com.socialvault.app.data.SocialIdWithPages
import com.socialvault.app.data.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In-memory fake of the Room DAO, so the real VaultRepository logic is
 * tested on the JVM: entries must land under the right email, with the
 * right kind, and a second entry must reuse the same email row.
 */
private class FakeDao : AccountDao {
    val emails = mutableListOf<EmailAccount>()
    val ids = mutableListOf<SocialId>()
    val pages = mutableListOf<FacebookPage>()
    private var nextEmailId = 1L
    private var nextId = 1L
    private val flow = MutableStateFlow<List<EmailWithIds>>(emptyList())

    private fun snapshot(): List<EmailWithIds> = emails.map { acc ->
        EmailWithIds(
            acc,
            ids.filter { it.emailAccountId == acc.id }
                .map { s -> SocialIdWithPages(s, pages.filter { it.socialIdId == s.id }) }
        )
    }

    private fun emit() {
        flow.value = snapshot()
    }

    override fun getAllEmails(): Flow<List<EmailWithIds>> = flow
    override suspend fun getAllEmailsSync(): List<EmailWithIds> = snapshot()
    override suspend fun findByEmail(email: String): EmailAccount? =
        emails.firstOrNull { it.email.equals(email, ignoreCase = true) }

    override suspend fun insertEmail(account: EmailAccount): Long {
        val id = nextEmailId++
        emails += account.copy(id = id)
        emit()
        return id
    }

    override suspend fun insertSocialId(socialId: SocialId): Long {
        val id = nextId++
        ids += socialId.copy(id = id)
        emit()
        return id
    }

    override suspend fun insertPages(pages: List<FacebookPage>) {
        this.pages += pages
        emit()
    }

    override suspend fun updateSocialId(socialId: SocialId) {
        val i = ids.indexOfFirst { it.id == socialId.id }
        if (i >= 0) ids[i] = socialId
        emit()
    }

    override suspend fun deletePagesFor(socialIdId: Long) {
        pages.removeAll { it.socialIdId == socialIdId }
        emit()
    }

    override suspend fun deleteSocialId(socialId: SocialId) {
        ids.removeAll { it.id == socialId.id }
        pages.removeAll { it.socialIdId == socialId.id }
        emit()
    }

    override suspend fun deleteEmail(account: EmailAccount) {
        emails.removeAll { it.id == account.id }
        val gone = ids.filter { it.emailAccountId == account.id }.map { it.id }
        ids.removeAll { it.emailAccountId == account.id }
        pages.removeAll { it.socialIdId in gone }
        emit()
    }

    override suspend fun deleteAllEmails() {
        emails.clear()
        ids.clear()
        pages.clear()
        emit()
    }
}

class RepositoryTest {

    @Test
    fun addAppEntryIsSavedAndListed() = runBlocking {
        val repo = VaultRepository(FakeDao())
        repo.addEntry(
            email = "ali@gmail.com", kind = "APP", platform = "",
            username = "ali", country = "", password = "pw1", appName = "WhatsApp"
        )
        val all = repo.getAllEmailsSync()
        assertEquals(1, all.size)
        assertEquals(1, all[0].ids.size)
        assertEquals("APP", all[0].ids[0].socialId.kind)
        assertEquals("WhatsApp", all[0].ids[0].socialId.appName)
    }

    @Test
    fun addWebsiteEntryIsSavedAndListed() = runBlocking {
        val repo = VaultRepository(FakeDao())
        repo.addEntry(
            email = "ali@gmail.com", kind = "WEBSITE", platform = "",
            username = "ali", country = "Pakistan", password = "pw1",
            siteName = "Daraz", url = "www.daraz.pk"
        )
        val all = repo.getAllEmailsSync()
        assertEquals(1, all.size)
        assertEquals("WEBSITE", all[0].ids[0].socialId.kind)
        assertEquals("Daraz", all[0].ids[0].socialId.siteName)
    }

    @Test
    fun secondEntryReusesSameEmail() = runBlocking {
        val dao = FakeDao()
        val repo = VaultRepository(dao)
        repo.addEntry(
            email = "ali@gmail.com", kind = "SOCIAL", platform = "Facebook",
            username = "ali.fb", country = "Pakistan", password = "pw1"
        )
        repo.addEntry(
            email = "ali@gmail.com", kind = "APP", platform = "",
            username = "ali", country = "", password = "pw2", appName = "JazzCash"
        )
        val all = repo.getAllEmailsSync()
        assertEquals(1, all.size) // one email card, not two
        assertEquals(2, all[0].ids.size)
        assertEquals(1, dao.emails.size)
    }

    @Test
    fun facebookPagesSavedWithSocialEntry() = runBlocking {
        val repo = VaultRepository(FakeDao())
        repo.addEntry(
            email = "a@b.com", kind = "SOCIAL", platform = "Facebook",
            username = "fb", country = "Pakistan", password = "pw",
            pages = listOf("Daily Discovery" to "1200")
        )
        val all = repo.getAllEmailsSync()
        assertEquals(1, all[0].ids[0].pages.size)
        assertEquals("Daily Discovery", all[0].ids[0].pages[0].name)
    }

    @Test
    fun updateEntryKeepsSameRow() = runBlocking {
        val dao = FakeDao()
        val repo = VaultRepository(dao)
        val id = repo.addEntry(
            email = "a@b.com", kind = "APP", platform = "",
            username = "u", country = "", password = "pw", appName = "WhatsApp"
        )
        val orig = dao.ids.first { it.id == id }
        repo.updateEntry(orig.copy(appName = "WhatsApp Business"), emptyList())
        val all = repo.getAllEmailsSync()
        assertEquals(1, all[0].ids.size)
        assertEquals("WhatsApp Business", all[0].ids[0].socialId.appName)
    }
}
