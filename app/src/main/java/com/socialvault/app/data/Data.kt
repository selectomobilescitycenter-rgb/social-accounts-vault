package com.socialvault.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One email address. An email can hold several entries:
 * social IDs (Facebook / Instagram / TikTok), websites and apps.
 */
@Entity(
    tableName = "email_accounts",
    indices = [Index(value = ["email"], unique = true)]
)
data class EmailAccount(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String
)

/**
 * One vault entry belonging to an email.
 *
 * kind:
 *  - "SOCIAL"  — Facebook / Instagram / TikTok (platform, username, country, pages)
 *  - "WEBSITE" — any website (siteName, url, username/email)
 *  - "APP"     — any other app (appName, optional packageName)
 *
 * Common to all kinds: username, password, category, notes,
 * recoveryEmail, recoveryPhone, totpSecret, createdAt.
 */
@Entity(
    tableName = "social_ids",
    foreignKeys = [
        ForeignKey(
            entity = EmailAccount::class,
            parentColumns = ["id"],
            childColumns = ["emailAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("emailAccountId")]
)
data class SocialId(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailAccountId: Long,
    val platform: String, // "Facebook" | "Instagram" | "TikTok" (SOCIAL only)
    val username: String,
    val country: String,
    val password: String,
    @ColumnInfo(defaultValue = "'SOCIAL'") val kind: String = "SOCIAL",
    @ColumnInfo(defaultValue = "''") val siteName: String = "",
    @ColumnInfo(defaultValue = "''") val url: String = "",
    @ColumnInfo(defaultValue = "''") val appName: String = "",
    @ColumnInfo(defaultValue = "''") val packageName: String = "",
    @ColumnInfo(defaultValue = "'Personal'") val category: String = "Personal",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "''") val recoveryEmail: String = "",
    @ColumnInfo(defaultValue = "''") val recoveryPhone: String = "",
    @ColumnInfo(defaultValue = "''") val totpSecret: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0
) {
    /** Human-readable name shown for this entry. */
    fun displayName(): String = when (kind) {
        "WEBSITE" -> siteName.ifBlank { url.ifBlank { username } }
        "APP" -> appName.ifBlank { username }
        else -> username.ifBlank { platform }
    }
}

/** A Facebook Page belonging to a Facebook ID. */
@Entity(
    tableName = "facebook_pages",
    foreignKeys = [
        ForeignKey(
            entity = SocialId::class,
            parentColumns = ["id"],
            childColumns = ["socialIdId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("socialIdId")]
)
data class FacebookPage(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val socialIdId: Long,
    val name: String,
    val followers: String
)

data class SocialIdWithPages(
    @Embedded val socialId: SocialId,
    @Relation(parentColumn = "id", entityColumn = "socialIdId")
    val pages: List<FacebookPage> = emptyList()
)

data class EmailWithIds(
    @Embedded val account: EmailAccount,
    @Relation(
        entity = SocialId::class,
        parentColumn = "id",
        entityColumn = "emailAccountId"
    )
    val ids: List<SocialIdWithPages> = emptyList()
)

@Dao
interface AccountDao {
    @Transaction
    @Query("SELECT * FROM email_accounts ORDER BY email COLLATE NOCASE ASC")
    fun getAllEmails(): Flow<List<EmailWithIds>>

    @Transaction
    @Query("SELECT * FROM email_accounts ORDER BY email COLLATE NOCASE ASC")
    suspend fun getAllEmailsSync(): List<EmailWithIds>

    @Query("SELECT * FROM email_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun findByEmail(email: String): EmailAccount?

    @Insert
    suspend fun insertEmail(account: EmailAccount): Long

    @Insert
    suspend fun insertSocialId(socialId: SocialId): Long

    @Insert
    suspend fun insertPages(pages: List<FacebookPage>)

    @Update
    suspend fun updateSocialId(socialId: SocialId)

    @Query("DELETE FROM facebook_pages WHERE socialIdId = :socialIdId")
    suspend fun deletePagesFor(socialIdId: Long)

    @Delete
    suspend fun deleteSocialId(socialId: SocialId)

    @Delete
    suspend fun deleteEmail(account: EmailAccount)

    @Query("DELETE FROM email_accounts")
    suspend fun deleteAllEmails()
}

/**
 * v1 -> v2: adds the generic-entry columns. Every v1 row becomes a SOCIAL
 * entry with the v1 values untouched, so data from the v1 app survives.
 * The DEFAULTs below must match the defaults Room generates for the
 * v2 entity (verified against the generated AppDatabase_Impl).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE social_ids ADD COLUMN kind TEXT NOT NULL DEFAULT 'SOCIAL'")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN siteName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN url TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN appName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN category TEXT NOT NULL DEFAULT 'Personal'")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN recoveryEmail TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN recoveryPhone TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN totpSecret TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE social_ids ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [EmailAccount::class, SocialId::class, FacebookPage::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "social_vault.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/** Small repository over the DAO; the UI never touches the DAO directly. */
class VaultRepository(private val dao: AccountDao) {

    fun getAllEmails(): Flow<List<EmailWithIds>> = dao.getAllEmails()

    suspend fun getAllEmailsSync(): List<EmailWithIds> = dao.getAllEmailsSync()

    /**
     * Add one entry of any kind. The email row is created on demand.
     * Returns the new entry's row id.
     */
    suspend fun addEntry(
        email: String,
        kind: String,
        platform: String,
        username: String,
        country: String,
        password: String,
        siteName: String = "",
        url: String = "",
        appName: String = "",
        packageName: String = "",
        category: String = "Personal",
        notes: String = "",
        recoveryEmail: String = "",
        recoveryPhone: String = "",
        totpSecret: String = "",
        pages: List<Pair<String, String>> = emptyList()
    ): Long {
        val cleanEmail = email.trim()
        val account = dao.findByEmail(cleanEmail)
        val accountId = account?.id ?: dao.insertEmail(EmailAccount(email = cleanEmail))
        val entryId = dao.insertSocialId(
            SocialId(
                emailAccountId = accountId,
                platform = platform,
                username = username.trim(),
                country = country.trim(),
                password = password,
                kind = kind,
                siteName = siteName.trim(),
                url = url.trim(),
                appName = appName.trim(),
                packageName = packageName.trim(),
                category = category,
                notes = notes.trim(),
                recoveryEmail = recoveryEmail.trim(),
                recoveryPhone = recoveryPhone.trim(),
                totpSecret = totpSecret.trim().replace(" ", ""),
                createdAt = System.currentTimeMillis()
            )
        )
        if (kind == "SOCIAL" && platform == "Facebook" && pages.isNotEmpty()) {
            dao.insertPages(
                pages.map { (name, followers) ->
                    FacebookPage(socialIdId = entryId, name = name, followers = followers)
                }
            )
        }
        return entryId
    }

    /** Backwards-compatible helper for the old social-only call path. */
    suspend fun addSocialId(
        email: String,
        platform: String,
        username: String,
        country: String,
        password: String,
        pages: List<Pair<String, String>>
    ) {
        addEntry(
            email = email,
            kind = "SOCIAL",
            platform = platform,
            username = username,
            country = country,
            password = password,
            pages = pages
        )
    }

    suspend fun updateEntry(entry: SocialId, pages: List<Pair<String, String>>) {
        dao.updateSocialId(entry)
        dao.deletePagesFor(entry.id)
        if (entry.kind == "SOCIAL" && entry.platform == "Facebook" && pages.isNotEmpty()) {
            dao.insertPages(
                pages.map { (name, followers) ->
                    FacebookPage(socialIdId = entry.id, name = name, followers = followers)
                }
            )
        }
    }

    suspend fun updateSocialId(socialId: SocialId, pages: List<Pair<String, String>>) {
        updateEntry(socialId, pages)
    }

    suspend fun deleteSocialId(idWithPages: SocialIdWithPages) {
        dao.deleteSocialId(idWithPages.socialId)
    }

    suspend fun deleteEmail(emailWithIds: EmailWithIds) {
        dao.deleteEmail(emailWithIds.account)
    }

    /**
     * Replace ALL vault data with the contents of a backup.
     * Used by Restore; runs in one transaction.
     */
    @Transaction
    suspend fun replaceAll(backup: BackupData) {
        dao.deleteAllEmails()
        for (backupEmail in backup.emails) {
            val accountId = dao.insertEmail(EmailAccount(email = backupEmail.email))
            for (entry in backupEmail.entries) {
                val entryId = dao.insertSocialId(
                    SocialId(
                        emailAccountId = accountId,
                        platform = entry.platform,
                        username = entry.username,
                        country = entry.country,
                        password = entry.password,
                        kind = entry.kind,
                        siteName = entry.siteName,
                        url = entry.url,
                        appName = entry.appName,
                        packageName = entry.packageName,
                        category = entry.category,
                        notes = entry.notes,
                        recoveryEmail = entry.recoveryEmail,
                        recoveryPhone = entry.recoveryPhone,
                        totpSecret = entry.totpSecret,
                        createdAt = entry.createdAt
                    )
                )
                if (entry.kind == "SOCIAL" && entry.platform == "Facebook") {
                    dao.insertPages(
                        entry.pages.map { page ->
                            FacebookPage(
                                socialIdId = entryId,
                                name = page.name,
                                followers = page.followers
                            )
                        }
                    )
                }
            }
        }
    }
}
