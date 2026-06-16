package app.fynlo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Person
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId


// ── Country code data ─────────────────────────────────────────────────────────
data class CountryCode(val code: String, val flag: String, val name: String) {
    val display get() = code
    val full    get() = "$code $name"
}

val COUNTRY_CODES = listOf(
    CountryCode("+91",  "🇮🇳", "India"),
    CountryCode("+1",   "🇺🇸", "USA/Canada"),
    CountryCode("+44",  "🇬🇧", "UK"),
    CountryCode("+61",  "🇦🇺", "Australia"),
    CountryCode("+971", "🇦🇪", "UAE"),
    CountryCode("+65",  "🇸🇬", "Singapore"),
    CountryCode("+60",  "🇲🇾", "Malaysia"),
    CountryCode("+966", "🇸🇦", "Saudi Arabia"),
    CountryCode("+974", "🇶🇦", "Qatar"),
    CountryCode("+968", "🇴🇲", "Oman"),
    CountryCode("+973", "🇧🇭", "Bahrain"),
    CountryCode("+49",  "🇩🇪", "Germany"),
    CountryCode("+33",  "🇫🇷", "France"),
    CountryCode("+81",  "🇯🇵", "Japan"),
    CountryCode("+86",  "🇨🇳", "China")
)

private fun displayContactId(name: String, id: String): String {
    val namePart = name
        .filter { it.isLetterOrDigit() }
        .take(8)
        .uppercase()
        .ifBlank { "CONTACT" }
    val idPart = id
        .filter { it.isLetterOrDigit() }
        .takeLast(4)
        .uppercase()
        .ifBlank { "0000" }
    return "$namePart-$idPart"
}

/** Parse a saved phone string into (CountryCode, localNumber).
 *  e.g. "+919876543210" → ("+91 🇮🇳 India", "9876543210")
 *  Falls back to +91 India if no match. */
fun parsePhone(saved: String): Pair<CountryCode, String> {
    val clean = saved.trim()
    // Try longest prefix first so +971 beats +97
    val match = COUNTRY_CODES.sortedByDescending { it.code.length }
        .firstOrNull { clean.startsWith(it.code) }
    return if (match != null) {
        Pair(match, clean.removePrefix(match.code).trimStart())
    } else {
        Pair(COUNTRY_CODES.first(), clean)   // default India, keep as-is
    }
}

// ── Screens ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PeopleScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val people by viewModel.people.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // C22 (3.2.71) — contacts import state. `loadedContacts` non-null
    // triggers the multi-select dialog. The permission launcher is wired
    // below; on grant it reads contacts off the IO dispatcher and pops
    // the dialog with the result.
    var loadedContacts by remember { mutableStateOf<List<app.fynlo.logic.ContactsReader.Entry>?>(null) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch(Dispatchers.IO) {
                val list = app.fynlo.logic.ContactsReader.read(context)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    loadedContacts = list
                    if (list.isEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "No contacts with phone numbers found on this device.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        } else {
            android.widget.Toast.makeText(
                context,
                "Can't import without Contacts permission. Add contacts manually instead.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Existing people indexed by normalised phone digits so the import
    // dialog can hide rows that are already in the contact book.
    val existingPhones = remember(people) {
        people.map { it.phone.filter(Char::isDigit) }.filter { it.isNotEmpty() }.toSet()
    }
    val filteredPeople = remember(people, searchQuery) {
        if (searchQuery.isBlank()) people
        else people.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    if (showAddDialog || editingPerson != null) {
        AddPersonDialog(
            initial   = editingPerson,
            onDismiss = { showAddDialog = false; editingPerson = null },
            onConfirm = { person ->
                if (editingPerson != null) {
                    viewModel.updatePerson(person)
                    viewModel.showFeedback("Contact updated")
                } else {
                    viewModel.addPerson(person)
                    viewModel.showFeedback("Contact added")
                }
                showAddDialog = false; editingPerson = null
            }
        )
    }

    // C22 (3.2.71) — contacts import multi-select dialog.
    loadedContacts?.let { contacts ->
        ImportContactsDialog(
            contacts = contacts,
            existingPhoneDigits = existingPhones,
            onDismiss = { loadedContacts = null },
            onImport = { picks ->
                scope.launch(Dispatchers.IO) {
                    picks.forEach { entry ->
                        viewModel.addPerson(Person(
                            // C03b Stage #4 (3.2.91) — switched from
                            // "P-${timestamp}-${hashSuffix}" (collision-prone
                            // under fast inserts; format-inconsistent with
                            // the rest of the codebase) to the canonical
                            // UUID v4 emitted by `Ids.newId()`.
                            id    = app.fynlo.logic.Ids.newId(),
                            name  = entry.name.trim(),
                            phone = entry.phone.trim(),
                        ))
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Imported ${picks.size} contact${if (picks.size == 1) "" else "s"}.",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        loadedContacts = null
                    }
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Contact Book", "Linked to loans & reminders")
        Box(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contacts") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            singleLine = true
        )

        // C22 (3.2.71) — import from system Contacts. Permission requested
        // on tap; the contract auto-skips the prompt and proceeds straight
        // to read if it's already granted.
        FilledTonalButton(
            onClick = {
                permLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Contacts, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import from Contacts", style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .semantics { testTagsAsResourceId = true }
                .testTag("people_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = FabBottomPadding)
        ) {
            // C19 (3.2.43) — sub-header text only shown when the list has
            // contacts. On empty state the shared EmptyState body says the
            // same thing — removing the redundant double-explanation
            // (audit C19 "Contact Book: redundant double-explanation").
            if (people.isNotEmpty()) {
                item {
                    Text(
                        "Contacts link loans to people and enable WhatsApp / SMS reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (people.isEmpty()) {
                item { EmptyPeopleState(onAdd = { showAddDialog = true }) }
            } else if (filteredPeople.isEmpty()) {
                item { EmptyPeopleSearchState(onClear = { searchQuery = "" }) }
            } else {
                itemsIndexed(filteredPeople, key = { _, p -> p.id }) { index, person ->
                    PersonCard(
                        person   = person,
                        onEdit   = { editingPerson = person },
                        onDelete = {
                            viewModel.deletePerson(person)
                            viewModel.showFeedback("Contact deleted")
                        }
                    )
                    if (index < filteredPeople.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
        } // close search Column
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, contentDescription = "Add Contact") }
        }
    }
}

// ── PersonCard ────────────────────────────────────────────────────────────────

@Composable
fun PersonCard(person: Person, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete contact?") },
            text  = { Text("Remove \"${person.name}\" from your contact book? Loans already linked to this contact are not affected. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    // Parse stored phone for clean display
    val (countryCode, localNumber) = remember(person.phone) { parsePhone(person.phone) }
    val displayPhone = when {
        person.phone.isBlank()    -> null
        localNumber.isNotBlank()  -> "${countryCode.display} $localNumber"
        else                      -> person.phone
    }

        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Avatar
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            person.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (person.id.isNotBlank()) {
                        Text(
                            displayContactId(person.name, person.id),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (displayPhone != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                displayPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "No phone - tap edit to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete", color = SemanticRed) },
                        onClick = { menuOpen = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress); showDeleteConfirm = true })
                }
            }
        }
}

// ── AddPersonDialog ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonDialog(initial: Person? = null, onDismiss: () -> Unit, onConfirm: (Person) -> Unit) {
    val isEdit = initial != null
    var name  by remember { mutableStateOf(initial?.name ?: "") }
    val id = remember(initial?.id) { initial?.id ?: app.fynlo.logic.Ids.newId() }
    val displayId = remember(name, id) { displayContactId(name, id) }

    // Parse existing phone
    val (initCode, initNumber) = remember { parsePhone(initial?.phone ?: "") }
    var selectedCode by remember { mutableStateOf(initCode) }
    var phoneNumber  by remember { mutableStateOf(initNumber) }
    var codeExpanded by remember { mutableStateOf(false) }

    // Live preview of the full number
    val fullPhone = if (phoneNumber.isNotBlank()) "${selectedCode.code}$phoneNumber" else ""

    // C22 dialog universalization (3.2.54) — migrated to canonical FormDialog.
    app.fynlo.ui.components.FormDialog(
        title = if (isEdit) "Edit Contact" else "Add New Contact",
        onDismiss = onDismiss,
    ) {
        app.fynlo.ui.components.FormSectionLabel("Full name")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text("e.g. Priya Sharma") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Phone number (for WhatsApp / SMS)")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = codeExpanded,
                onExpandedChange = { codeExpanded = !codeExpanded },
                modifier = Modifier.width(120.dp)
            ) {
                OutlinedTextField(
                    value = selectedCode.display,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codeExpanded) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .width(120.dp)
                )
                ExposedDropdownMenu(
                    expanded = codeExpanded,
                    onDismissRequest = { codeExpanded = false }
                ) {
                    COUNTRY_CODES.forEach { cc ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    cc.full,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            onClick = { selectedCode = cc; codeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { c -> c.isDigit() } },
                placeholder = { Text("9876543210") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        if (phoneNumber.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "WhatsApp will use: $fullPhone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Contact ID")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = displayId,
            onValueChange = {},
            readOnly = true,
            supportingText = { Text("Generated automatically") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) onConfirm(
                    Person(
                        id    = id,
                        name  = name.trim(),
                        phone = fullPhone.trim()
                    )
                )
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500),
        ) {
            Text(
                if (isEdit) "Update Contact" else "Save Contact",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        app.fynlo.ui.components.DisabledButtonHint(
            if (name.isBlank()) "Enter a name to continue" else null
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

/**
 * C19 (3.2.43) — bespoke EmptyPeopleState migrated to the shared
 * [EmptyState] composable per audit §C19. Wraps in a centred Box so the
 * EmptyState (which uses `fillMaxSize`) renders inside the LazyColumn item
 * slot without expanding to swallow the rest of the screen.
 */
@Composable
fun EmptyPeopleState(onAdd: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
        app.fynlo.ui.theme.EmptyState(
            icon        = Icons.Default.Person,
            title       = "No contacts yet",
            subtitle    = "Contacts link loans to people and enable WhatsApp / SMS reminders.",
            actionLabel = "Add contact",
            onAction    = onAdd,
        )
    }
}

@Composable
fun EmptyPeopleSearchState(onClear: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            Text(
                "No matching contacts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Clear search to see everyone in your contact book.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onClear) {
                Text("Clear search")
            }
        }
    }
}

/**
 * C22 (3.2.71) — multi-select dialog for system-Contacts import.
 *
 * Surfaces every readable contact with an in-line checkbox; pre-checks
 * nothing (user opts in deliberately). Contacts whose number already
 * exists in the Fynlo contact book (matched on normalised digits) are
 * hidden — re-importing them would create duplicates, and we want the
 * dialog to focus on "what's new".
 *
 * Quick-search filter at the top so a phone with hundreds of contacts
 * stays browseable. "Select all" / "Clear" buttons act on the filtered
 * subset so the user can bulk-import a search-narrowed group.
 */
@Composable
private fun ImportContactsDialog(
    contacts: List<app.fynlo.logic.ContactsReader.Entry>,
    existingPhoneDigits: Set<String>,
    onDismiss: () -> Unit,
    onImport: (List<app.fynlo.logic.ContactsReader.Entry>) -> Unit,
) {
    // Hide already-imported contacts to keep the list focused on new ones.
    val newContacts = remember(contacts, existingPhoneDigits) {
        contacts.filter { entry ->
            val digits = entry.phone.filter(Char::isDigit)
            digits.isEmpty() || digits !in existingPhoneDigits
        }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(newContacts, query) {
        if (query.isBlank()) newContacts
        else newContacts.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.phone.contains(query)
        }
    }
    // Selection keyed by index into the unchanged `newContacts` list.
    val selected = remember(contacts) { mutableStateMapOf<Int, Boolean>() }
    val newIndexByEntry = remember(newContacts) { newContacts.withIndex().associate { (i, e) -> e to i } }
    val pickedCount = selected.count { it.value }

    app.fynlo.ui.components.FormDialog(
        title = "Import contacts",
        onDismiss = onDismiss,
    ) {
        Text(
            if (newContacts.isEmpty()) "No new contacts to import - your contact book already covers everyone with a phone number."
            else "${newContacts.size} contacts available. Tick the ones you want to add.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (newContacts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name or number") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    filtered.forEach { selected[newIndexByEntry[it] ?: -1] = true }
                }) { Text("Select all (${filtered.size})", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    filtered.forEach { selected[newIndexByEntry[it] ?: -1] = false }
                }) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.weight(1f))
                Text("$pickedCount picked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.name + it.phone }) { entry ->
                    val idx = newIndexByEntry[entry] ?: return@items
                    val isPicked = selected[idx] == true
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { selected[idx] = !isPicked }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isPicked, onCheckedChange = { selected[idx] = it })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(entry.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val picks = selected.filter { it.value }.keys.mapNotNull { newContacts.getOrNull(it) }
                onImport(picks)
            },
            enabled = pickedCount > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text(
                if (pickedCount == 0) "Pick at least one contact"
                else "Import $pickedCount contact${if (pickedCount == 1) "" else "s"}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
