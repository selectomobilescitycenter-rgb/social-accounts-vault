package com.socialvault.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.socialvault.app.data.BackupData
import com.socialvault.app.data.EmailWithIds
import com.socialvault.app.data.PasswordStrength
import com.socialvault.app.data.SocialIdWithPages
import com.socialvault.app.data.VaultPrefs
import com.socialvault.app.data.VaultRepository
import com.socialvault.app.data.buildBackupJson
import com.socialvault.app.data.generatePassword
import com.socialvault.app.data.parseBackupJson
import com.socialvault.app.data.passwordStrength
import com.socialvault.app.data.strengthLabel
import com.socialvault.app.data.totpCode
import com.socialvault.app.data.totpSecondsRemaining
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All fields of the Add/Edit entry form, for any entry kind. */
data class EntryFormData(
    val email: String,
    val kind: String, // SOCIAL | WEBSITE | APP
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
    val pages: List<Pair<String, String>>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    repository: VaultRepository,
    prefs: VaultPrefs,
    darkMode: Boolean,
    onToggleDark: () -> Unit,
    onChangePin: () -> Unit,
    onExternalIntent: () -> Unit
) {
    val context = LocalContext.current
    val emails by repository.getAllEmails().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // List state, so stat-tile taps can scroll the list into view.
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf("All") } // All | SOCIAL | WEBSITE | APP
    var sortNewest by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    // 2nd page inside the panel: the tapped entry's full data.
    var selectedEntry by remember { mutableStateOf<SocialIdWithPages?>(null) }
    var editing by remember { mutableStateOf<Pair<String, SocialIdWithPages>?>(null) }
    var deleteId by remember { mutableStateOf<Pair<String, SocialIdWithPages>?>(null) }
    var deleteEmail by remember { mutableStateOf<EmailWithIds?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var pendingEntry by remember { mutableStateOf<EntryFormData?>(null) }
    var duplicateName by remember { mutableStateOf("") }

    // The panel always reads from the live list, so it updates after edits/deletes.
    val selectedEmail = selectedId?.let { id -> emails.find { it.account.id == id } }

    /** Stat-tile tap: every tile does something real. Platform tiles filter
     * the list by kind — tapping the same tile again clears the filter, so
     * a filter can never silently hide the rest of the vault. Count tiles
     * show the list, and with exactly one email they open that email's
     * detail panel directly. */
    fun onStatTap(label: String) {
        val target = when (label) {
            "Facebook", "Instagram", "TikTok" -> "SOCIAL"
            "Websites" -> "WEBSITE"
            "Apps" -> "APP"
            else -> "All" // Emails, Total IDs, FB Pages
        }
        kindFilter = if (kindFilter == target) "All" else target
        if (kindFilter == "All" && target == "All" && emails.size == 1) {
            selectedId = emails.first().account.id
        } else {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    // Backup: write all vault data as JSON through the system file picker.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = buildBackupJson(repository.getAllEmailsSync())
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Restore: read a JSON backup, then ask before replacing all data.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText() ?: ""
                    pendingRestore = parseBackupJson(text)
                } catch (e: Exception) {
                    Toast.makeText(context, "Invalid backup file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Kind filter + search (matches site/app names, URLs, notes, category too).
    // Pure function in Filtering.kt — unit-tested (FilterTest).
    val filtered = remember(query, emails, kindFilter, sortNewest) {
        filterEmails(emails, query, kindFilter, sortNewest)
    }

    val totalIds = emails.sumOf { it.ids.size }
    val fbCount = emails.sumOf { e -> e.ids.count { it.socialId.platform == "Facebook" } }
    val igCount = emails.sumOf { e -> e.ids.count { it.socialId.platform == "Instagram" } }
    val ttCount = emails.sumOf { e -> e.ids.count { it.socialId.platform == "TikTok" } }
    val pagesCount = emails.sumOf { e -> e.ids.sumOf { it.pages.size } }
    val webCount = emails.sumOf { e -> e.ids.count { it.socialId.kind == "WEBSITE" } }
    val appCount = emails.sumOf { e -> e.ids.count { it.socialId.kind == "APP" } }
    val stats = listOf(
        "Emails" to emails.size,
        "Total IDs" to totalIds,
        "Facebook" to fbCount,
        "Instagram" to igCount,
        "TikTok" to ttCount,
        "FB Pages" to pagesCount,
        "Websites" to webCount,
        "Apps" to appCount
    )

    /** Save a form entry (add or edit), then close the form.
     * The saved entry must be visible afterwards: clear any kind filter
     * and search, so a freshly saved Website/App entry can never stay
     * hidden behind a stuck "Social" filter. A Toast confirms the save. */
    fun saveEntry(data: EntryFormData) {
        val currentEditing = editing
        kindFilter = "All"
        query = ""
        val savedName = when (data.kind) {
            "WEBSITE" -> data.siteName.ifBlank { data.url }
            "APP" -> data.appName.ifBlank { data.username }
            else -> data.username.ifBlank { data.platform }
        }
        scope.launch {
            if (currentEditing != null) {
                val orig = currentEditing.second.socialId
                repository.updateEntry(
                    orig.copy(
                        kind = data.kind,
                        platform = data.platform,
                        username = data.username,
                        country = data.country,
                        password = data.password,
                        siteName = data.siteName,
                        url = data.url,
                        appName = data.appName,
                        packageName = data.packageName,
                        category = data.category,
                        notes = data.notes,
                        recoveryEmail = data.recoveryEmail,
                        recoveryPhone = data.recoveryPhone,
                        totpSecret = data.totpSecret
                    ),
                    data.pages
                )
            } else {
                repository.addEntry(
                    email = data.email,
                    kind = data.kind,
                    platform = data.platform,
                    username = data.username,
                    country = data.country,
                    password = data.password,
                    siteName = data.siteName,
                    url = data.url,
                    appName = data.appName,
                    packageName = data.packageName,
                    category = data.category,
                    notes = data.notes,
                    recoveryEmail = data.recoveryEmail,
                    recoveryPhone = data.recoveryPhone,
                    totpSecret = data.totpSecret,
                    pages = data.pages
                )
            }
            Toast.makeText(context, "Saved: $savedName", Toast.LENGTH_SHORT).show()
        }
        showAdd = false
        editing = null
    }

    /** Save attempt: warn first if the password is already used elsewhere. */
    fun attemptSave(data: EntryFormData) {
        val editingId = editing?.second?.socialId?.id
        val clash = emails.flatMap { it.ids }.map { it.socialId }
            .firstOrNull { it.password == data.password && it.id != editingId }
        if (clash != null) {
            pendingEntry = data
            duplicateName = clash.displayName()
        } else {
            saveEntry(data)
        }
    }

    // The whole home screen is ONE LazyColumn, so it always scrolls. The old
    // plain Column starved the inner list of height: the header filled the
    // whole screen, the list got ~0dp, and nothing could scroll at all.
    // Header first, then the email cards as list items.
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
        Text(
            "🔐 My Accounts",
            style = TextStyle(
                brush = TitleGradient,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )
        )
        Text(
            "Social IDs, websites and apps — tap an email card to open its panel.",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(10.dp))

        // Top-bar actions: dark mode, backup, restore, change PIN.
        // Each button takes an equal share and its label never wraps.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onToggleDark,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(if (darkMode) "☀️ Light" else "🌙 Dark", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    onExternalIntent()
                    backupLauncher.launch("social-accounts-vault-backup.json")
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("💾 Backup", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    onExternalIntent()
                    restoreLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("📥 Restore", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onChangePin,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🔑 PIN", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "⚠️ Backup file mein passwords plain text mein hote hain — private rakhein.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
        Spacer(Modifier.height(10.dp))

        // Colour-coded stat chips in ONE horizontally scrollable row, so the
        // first email card stays visible on the first screen. Every chip is
        // tappable and does something real (see onStatTap).
        Text(
            "👆 Kisi bhi dabbe par tap karein — list khul jayegi",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats.forEachIndexed { index, (label, value) ->
                StatTile(
                    label = label,
                    value = value,
                    gradient = statGradient(index),
                    onClick = { onStatTap(label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("🔍 Search accounts…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Box(
                modifier = Modifier
                    .background(AddButtonGradient, RoundedCornerShape(999.dp))
                    .clickable { showAdd = true }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    "＋ Add",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Kind filter chips + sort toggle.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All" to "All", "SOCIAL" to "Social", "WEBSITE" to "Websites", "APP" to "Apps")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = kindFilter == value,
                        onClick = { kindFilter = value },
                        label = { Text(label) }
                    )
                }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sortNewest = !sortNewest }) {
                Text(if (sortNewest) "⇅ Newest" else "⇅ A–Z", fontWeight = FontWeight.Black)
            }
        }
        // When a filter is on, say so in plain words — a hidden filter is
        // what used to make saved Website/App entries "disappear".
        if (kindFilter != "All") {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { kindFilter = "All" }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔎 Filter: ${kindLabel(kindFilter)} — sirf ${kindLabel(kindFilter)} dikh raha hai",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Text(
                    "✕ Clear",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
            }
        }

        if (emails.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No accounts yet.\nTap ＋ Add to add your first one.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp
                    )
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No accounts match that search.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp
                    )
                }
            }
        } else {
            items(filtered, key = { it.account.id }) { email ->
                EmailCard(
                    email = email,
                    onOpen = { selectedId = email.account.id },
                    onExternalIntent = onExternalIntent
                )
            }
        }
    }

    // Slide-in detail panel (bottom sheet) for the tapped email.
    // First page: grouped list of just the IDs. Tap one → 2nd page with
    // that ID's full data. The detail always reads the live entry, so an
    // edit made while it is open shows the new values.
    if (selectedEmail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedId = null; selectedEntry = null },
            sheetState = sheetState
        ) {
            val liveEntry = selectedEntry?.let { sel ->
                selectedEmail.ids.find { it.socialId.id == sel.socialId.id }
            }
            if (liveEntry != null) {
                EntryDetail(
                    idw = liveEntry,
                    onBack = { selectedEntry = null },
                    onEdit = { editing = selectedEmail.account.email to liveEntry },
                    onDelete = { deleteId = selectedEmail.account.email to liveEntry }
                )
            } else {
                DetailPanel(
                    email = selectedEmail,
                    onSelectEntry = { selectedEntry = it },
                    onDeleteEmail = { deleteEmail = selectedEmail }
                )
            }
        }
    }

    // Add Entry form.
    if (showAdd) {
        AccountFormDialog(
            emailText = null,
            editing = null,
            onDismiss = { showAdd = false },
            onSaveAttempt = { attemptSave(it) }
        )
    }

    // Edit Entry form.
    editing?.let { (emailText, idWithPages) ->
        AccountFormDialog(
            emailText = emailText,
            editing = idWithPages,
            onDismiss = { editing = null },
            onSaveAttempt = { attemptSave(it) }
        )
    }

    // Duplicate-password warning: allow saving anyway.
    pendingEntry?.let { data ->
        AlertDialog(
            onDismissRequest = {
                pendingEntry = null
                Toast.makeText(context, "Not saved", Toast.LENGTH_SHORT).show()
            },
            title = { Text("Password already used", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Ye password pehle se \"$duplicateName\" par use ho raha hai. " +
                        "Same password do jagah use karna safe nahi hota. Phir bhi save karein?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingEntry = null
                        saveEntry(data)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) {
                    Text("Save Anyway", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    pendingEntry = null
                    Toast.makeText(context, "Not saved", Toast.LENGTH_SHORT).show()
                }) { Text("Don't save") }
            }
        )
    }

    // Restore confirmation: replaces ALL current data.
    pendingRestore?.let { data ->
        val emailCount = data.emails.size
        val entryCount = data.emails.sumOf { it.entries.size }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This will REPLACE all current data with the backup " +
                        "($emailCount emails, $entryCount entries). " +
                        "Your current data will be deleted. Continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.replaceAll(data)
                            Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                        }
                        pendingRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenStart)
                ) {
                    Text("Restore", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }

    // Two-step delete confirmations.
    deleteId?.let { (emailText, idWithPages) ->
        DeleteConfirmDialog(
            title = "Delete this entry?",
            text = "This will remove ${idWithPages.socialId.displayName()} " +
                "(${kindLabel(idWithPages.socialId.kind)}) from $emailText.",
            onConfirm = {
                scope.launch { repository.deleteSocialId(idWithPages) }
                deleteId = null
                selectedEntry = null
            },
            onDismiss = { deleteId = null }
        )
    }
    deleteEmail?.let { email ->
        DeleteConfirmDialog(
            title = "Delete this whole email?",
            text = "This will remove ${email.account.email} and all " +
                "${email.ids.size} entries on it. This cannot be undone.",
            onConfirm = {
                scope.launch { repository.deleteEmail(email) }
                selectedId = null
                selectedEntry = null
                deleteEmail = null
            },
            onDismiss = { deleteEmail = null }
        )
    }
}

/** Compact, tappable stat chip: value + label, with a Material ripple on tap. */
@Composable
fun StatTile(label: String, value: Int, gradient: Brush, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailCard(
    email: EmailWithIds,
    onOpen: () -> Unit,
    onExternalIntent: () -> Unit
) {
    val context = LocalContext.current
    val first = email.ids.firstOrNull()?.socialId
    val fbPages = email.ids.sumOf { it.pages.size }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                first?.let { entryGradient(it) } ?: cardGradient("Facebook"),
                RoundedCornerShape(26.dp)
            )
            .border(3.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
            .clickable { onOpen() }
            .padding(20.dp)
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color.White.copy(alpha = 0.28f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(first?.let { entryIcon(it) } ?: "🔐", fontSize = 30.sp)
                }
                if (fbPages > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "$fbPages Pages",
                            color = FacebookBlue,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                email.account.email,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Password: ••••••••",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                email.ids.forEach { idw ->
                    val id = idw.socialId
                    val chipText = when (id.kind) {
                        "WEBSITE" -> "🌐 ${id.displayName()} • ${id.username}"
                        "APP" -> "📱 ${id.displayName()} • ${id.username}"
                        else -> "${platformIcon(id.platform)} ${id.platform} • " +
                            "${id.username} • ${flagFor(id.country)} " +
                            id.country.ifBlank { "—" }
                    }
                    Chip(chipText)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${email.ids.size} ${if (email.ids.size > 1) "entries" else "entry"} on this email",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "👆 Card par tap karein — IDs dekhein, password copy karein",
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(12.dp))
            // Primary action: open the detail panel to view the IDs on this
            // email and copy any email/password. This is the button users
            // were missing — the panel used to open only by tapping the
            // card body, with nothing on the card saying so.
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "👁 View IDs & Copy Password",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(8.dp))
            // Secondary action: opens the login page / website / app
            // OUTSIDE the vault (in the browser or the installed app).
            Button(
                onClick = {
                    first?.let {
                        onExternalIntent()
                        if (!openEntry(context, it)) {
                            Toast.makeText(context, "Nothing to open for this entry", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.28f),
                    contentColor = Color.White
                )
            ) {
                Text(
                    first?.let { entryActionLabel(it) } ?: "🌐 Open Login Page →",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPanel(
    email: EmailWithIds,
    onSelectEntry: (SocialIdWithPages) -> Unit,
    onDeleteEmail: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fbPages = email.ids.sumOf { it.pages.size }
    val countries = email.ids.map { it.socialId.country }
        .filter { it.isNotBlank() }
        .distinct()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitleGradient, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        email.account.email,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🎫 ${email.ids.size} entries  •  📄 $fbPages Facebook Pages",
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(email.account.email))
                        Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        "📋 Copy",
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("🌍 Countries on this email:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (countries.isEmpty()) {
                Text("—", fontWeight = FontWeight.Bold)
            } else {
                countries.forEach { c ->
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(999.dp)
                            )
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${flagFor(c)} $c",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        // Grouped by kind/platform: Apps, Websites, then one section per
        // social platform — never mixed in one list.
        val sections = remember(email.ids) { groupEntries(email.ids) }
        sections.forEach { section ->
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(section.icon, fontSize = 20.sp)
                Text(
                    section.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    "(${section.entries.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            section.entries.forEach { idw ->
                EntryRow(idw = idw, onClick = { onSelectEntry(idw) })
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDeleteEmail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFE3E3),
                contentColor = Color(0xFFB00020)
            )
        ) {
            Text("🗑 Delete this whole email", fontWeight = FontWeight.Black)
        }
    }
}

// 2nd page: one saved ID's full data with copy options. No "Open" button
// (removed in v1.6) — the data is here, with Show/Copy and Edit/Delete.
@Composable
fun EntryDetail(
    idw: SocialIdWithPages,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back to IDs", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        IdBlock(idw = idw, onEdit = onEdit, onDelete = onDelete)
    }
}

/** One saved ID as a compact row: tap it to open its data (2nd page). */
@Composable
fun EntryRow(idw: SocialIdWithPages, onClick: () -> Unit) {
    val id = idw.socialId
    val subtitle = when (id.kind) {
        "SOCIAL" -> "Social • ${id.platform}" +
            (if (id.country.isNotBlank()) " • ${flagFor(id.country)} ${id.country}" else "")
        "WEBSITE" -> id.url.ifBlank { id.username.ifBlank { "—" } }
        else -> id.username.ifBlank { id.packageName.ifBlank { "—" } }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(entryIcon(id), fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(
                id.displayName(),
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                maxLines = 1
            )
            Text(
                subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            "›",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun IdBlock(
    idw: SocialIdWithPages,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showPassword by remember { mutableStateOf(false) }
    val id = idw.socialId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(3.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${entryIcon(id)} ${id.displayName()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f, fill = false)
            )
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    id.category,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            kindLabel(id.kind) + when (id.kind) {
                "SOCIAL" -> " • ${id.platform}"
                "WEBSITE" -> if (id.url.isNotBlank()) " • ${id.url}" else ""
                else -> if (id.packageName.isNotBlank()) " • ${id.packageName}" else ""
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabelValue("Username / Email", id.username.ifBlank { "—" })
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(id.username))
                Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
            }) {
                Text("📋 Copy Email")
            }
        }
        if (id.kind == "WEBSITE" && id.url.isNotBlank()) {
            LabelValue("Website", id.url)
        }
        if (id.kind == "APP" && id.packageName.isNotBlank()) {
            LabelValue("App package", id.packageName)
        }
        if (id.country.isNotBlank()) {
            LabelValue("Country", "${flagFor(id.country)} ${id.country}")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabelValue(
                    "Password",
                    if (showPassword) id.password else "••••••••"
                )
            }
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(if (showPassword) "Hide" else "Show")
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(id.password))
                Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
            }) {
                Text("📋 Copy Password")
            }
        }
        if (id.totpSecret.isNotBlank()) {
            TotpRow(secret = id.totpSecret)
        }
        if (id.notes.isNotBlank()) {
            LabelValue("Notes", id.notes)
        }
        if (id.recoveryEmail.isNotBlank()) {
            LabelValue("Recovery email", id.recoveryEmail)
        }
        if (id.recoveryPhone.isNotBlank()) {
            LabelValue("Recovery phone", id.recoveryPhone)
        }
        if (id.kind == "SOCIAL" && id.platform == "Facebook") {
            Text(
                "Facebook Pages • ${idw.pages.size}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (idw.pages.isEmpty()) {
                Text(
                    "No Pages added for this ID yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                idw.pages.forEach { p ->
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(p.name, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "👥 ${p.followers} followers",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text("✏️ Edit")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFB00020)
                )
            ) {
                Text("Delete this entry")
            }
        }
    }
}

/** Live 6-digit 2FA code with a 30-second countdown bar. */
@Composable
fun TotpRow(secret: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val code = remember(secret, now / 30_000L) { totpCode(secret, now) }
    val remaining = totpSecondsRemaining(now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            "2FA Code",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        if (code == null) {
            Text(
                "Invalid 2FA secret — check the code you saved.",
                color = Color(0xFFB00020),
                fontWeight = FontWeight.Bold
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code.chunked(3).joinToString(" "),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "2FA code copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("📋 Copy")
                }
                Text(
                    "${remaining}s",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { remaining / 30f },
                modifier = Modifier.fillMaxWidth(),
                color = GreenStart
            )
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountFormDialog(
    emailText: String?,
    editing: SocialIdWithPages?,
    onDismiss: () -> Unit,
    onSaveAttempt: (EntryFormData) -> Unit
) {
    val orig = editing?.socialId
    var email by remember { mutableStateOf(emailText ?: "") }
    var kind by remember { mutableStateOf(orig?.kind ?: "SOCIAL") }
    var platform by remember { mutableStateOf(orig?.platform ?: "Facebook") }
    var username by remember { mutableStateOf(orig?.username ?: "") }
    var country by remember { mutableStateOf(orig?.country ?: "") }
    var password by remember { mutableStateOf(orig?.password ?: "") }
    var siteName by remember { mutableStateOf(orig?.siteName ?: "") }
    var url by remember { mutableStateOf(orig?.url ?: "") }
    var appName by remember { mutableStateOf(orig?.appName ?: "") }
    var packageName by remember { mutableStateOf(orig?.packageName ?: "") }
    var category by remember { mutableStateOf(orig?.category ?: "Personal") }
    var notes by remember { mutableStateOf(orig?.notes ?: "") }
    var recoveryEmail by remember { mutableStateOf(orig?.recoveryEmail ?: "") }
    var recoveryPhone by remember { mutableStateOf(orig?.recoveryPhone ?: "") }
    var totpSecret by remember { mutableStateOf(orig?.totpSecret ?: "") }
    var pagesText by remember {
        mutableStateOf(
            editing?.pages?.joinToString("\n") { "${it.name} - ${it.followers}" } ?: ""
        )
    }
    var showPassword by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }
    var passLen by remember { mutableStateOf(16) }
    val isEditing = editing != null
    val strength = passwordStrength(password)
    val canSave = email.isNotBlank() && password.isNotBlank() && when (kind) {
        "WEBSITE" -> siteName.isNotBlank() && url.isNotBlank()
        "APP" -> appName.isNotBlank()
        else -> username.isNotBlank()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    if (isEditing) "Edit Entry" else "＋ Add Entry",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(12.dp))

                // Entry kind selector.
                Text("Type", fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SOCIAL" to "Social", "WEBSITE" to "Website", "APP" to "App")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = kind == value,
                                onClick = { kind = value },
                                label = { Text(label) }
                            )
                        }
                }
                Spacer(Modifier.height(10.dp))

                if (isEditing) {
                    Text("Email: $email", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("e.g. you@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (kind == "SOCIAL") {
                    Text("Platform", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Facebook", "Instagram", "TikTok", "YouTube").forEach { p ->
                            FilterChip(
                                selected = platform == p,
                                onClick = { platform = p },
                                label = { Text(p) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        placeholder = { Text("@username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (kind == "WEBSITE") {
                    OutlinedTextField(
                        value = siteName,
                        onValueChange = { siteName = it },
                        label = { Text("Website name") },
                        placeholder = { Text("e.g. Daraz") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Website URL") },
                        placeholder = { Text("e.g. www.daraz.pk") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (kind == "APP") {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App name") },
                        placeholder = { Text("e.g. WhatsApp, JazzCash") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("App package (optional)") },
                        placeholder = { Text("e.g. com.whatsapp — blank = auto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Country (social + website; optional for websites).
                if (kind != "APP") {
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = countryExpanded,
                        onExpandedChange = { countryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Country" + if (kind == "WEBSITE") " (optional)" else "") },
                            placeholder = { Text("e.g. Pakistan") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = countryExpanded,
                            onDismissRequest = { countryExpanded = false }
                        ) {
                            COUNTRIES.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = { country = c; countryExpanded = false }
                                )
                            }
                        }
                    }
                }

                // Password with generator + strength meter.
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show")
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { password = generatePassword(passLen) },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("🎲", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Length:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    listOf(12, 16, 20).forEach { len ->
                        FilterChip(
                            selected = passLen == len,
                            onClick = { passLen = len },
                            label = { Text("$len") }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val strengthColor = when (strength) {
                        PasswordStrength.WEAK -> Color(0xFFD32F2F)
                        PasswordStrength.MEDIUM -> Color(0xFFF57C00)
                        PasswordStrength.STRONG -> GreenStart
                    }
                    Text(
                        strengthLabel(strength),
                        fontWeight = FontWeight.Black,
                        color = strengthColor
                    )
                }
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            when (strength) {
                                PasswordStrength.WEAK -> 0.33f
                                PasswordStrength.MEDIUM -> 0.66f
                                PasswordStrength.STRONG -> 1f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = when (strength) {
                            PasswordStrength.WEAK -> Color(0xFFD32F2F)
                            PasswordStrength.MEDIUM -> Color(0xFFF57C00)
                            PasswordStrength.STRONG -> GreenStart
                        }
                    )
                }

                // Category.
                Spacer(Modifier.height(10.dp))
                Text("Category", fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }

                // Notes + recovery + 2FA.
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = recoveryEmail,
                    onValueChange = { recoveryEmail = it },
                    label = { Text("Recovery email (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = recoveryPhone,
                    onValueChange = { recoveryPhone = it },
                    label = { Text("Recovery phone (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = totpSecret,
                    onValueChange = { totpSecret = it },
                    label = { Text("2FA secret (optional)") },
                    placeholder = { Text("Base32 code from the site/app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (kind == "SOCIAL" && platform == "Facebook") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { pagesText = it },
                        label = { Text("Facebook Pages (one per line: Page name - followers)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (canSave) {
                                onSaveAttempt(
                                    EntryFormData(
                                        email = email,
                                        kind = kind,
                                        platform = if (kind == "SOCIAL") platform else "",
                                        username = username,
                                        country = country,
                                        password = password,
                                        siteName = siteName,
                                        url = url,
                                        appName = appName,
                                        packageName = packageName,
                                        category = category,
                                        notes = notes,
                                        recoveryEmail = recoveryEmail,
                                        recoveryPhone = recoveryPhone,
                                        totpSecret = totpSecret,
                                        pages = parsePages(pagesText)
                                    )
                                )
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenStart)
                    ) {
                        Text("Save", fontWeight = FontWeight.Black)
                    }
                }
                if (!isEditing) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Saved only on this device. Nothing is sent to any server.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB00020)
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
