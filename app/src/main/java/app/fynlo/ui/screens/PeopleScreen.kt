package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
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


// ── Country code data ─────────────────────────────────────────────────────────
data class CountryCode(val code: String, val flag: String, val name: String) {
    val display get() = "$flag $code"
    val full    get() = "$code $flag $name"
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

@Composable
fun PeopleScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val people by viewModel.people.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    if (showAddDialog || editingPerson != null) {
        AddPersonDialog(
            initial   = editingPerson,
            onDismiss = { showAddDialog = false; editingPerson = null },
            onConfirm = { person ->
                if (editingPerson != null) viewModel.updatePerson(person)
                else viewModel.addPerson(person)
                showAddDialog = false; editingPerson = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Contact Book", "Linked to loans & reminders")
        Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                                Text(
                    "Contacts link loans to people and enable WhatsApp / SMS reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (people.isEmpty()) {
                item { EmptyPeopleState(onAdd = { showAddDialog = true }) }
            } else {
                items(people, key = { it.id }) { person ->
                    PersonCard(
                        person   = person,
                        onEdit   = { editingPerson = person },
                        onDelete = { viewModel.deletePerson(person) }
                    )
                }
            }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, contentDescription = "Add Contact") }
    }
        }
}

// ── PersonCard ────────────────────────────────────────────────────────────────
    }

@Composable
fun PersonCard(person: Person, onEdit: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // Parse stored phone for clean display
    val (countryCode, localNumber) = remember(person.phone) { parsePhone(person.phone) }
    val displayPhone = when {
        person.phone.isBlank()    -> null
        localNumber.isNotBlank()  -> "${countryCode.display} $localNumber"
        else                      -> person.phone
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp).fillMaxWidth(),
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
                            "ID: ${person.id}",
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
                            "No phone — tap edit to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete() }) {
                    Icon(Icons.Default.Delete, "Delete", tint = SemanticRed.copy(alpha = 0.6f))
                }
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
    var id    by remember { mutableStateOf(initial?.id ?: "P-${(100..999).random()}") }

    // Parse existing phone
    val (initCode, initNumber) = remember { parsePhone(initial?.phone ?: "") }
    var selectedCode by remember { mutableStateOf(initCode) }
    var phoneNumber  by remember { mutableStateOf(initNumber) }
    var codeExpanded by remember { mutableStateOf(false) }

    // Live preview of the full number
    val fullPhone = if (phoneNumber.isNotBlank()) "${selectedCode.code}$phoneNumber" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Contact" else "Add New Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Name
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Phone label
                Text(
                    "Phone Number (for WhatsApp / SMS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Country code + number row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Country code dropdown — shows flag + code only
                    ExposedDropdownMenuBox(
                        expanded = codeExpanded,
                        onExpandedChange = { codeExpanded = !codeExpanded },
                        modifier = Modifier.width(120.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCode.display,  // "🇮🇳 +91"
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codeExpanded) },
                            singleLine = true,
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
                                            cc.full,  // "🇮🇳 +91 India"
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    onClick = { selectedCode = cc; codeExpanded = false }
                                )
                            }
                        }
                    }

                    // Local number (digits only)
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("9876543210") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Full number preview — clean, unambiguous
                if (phoneNumber.isNotBlank()) {
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

                // ID (new contacts only)
                if (!isEdit) {
                    OutlinedTextField(
                        value = id, onValueChange = { id = it },
                        label = { Text("Unique ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(
                        Person(
                            id    = id.ifBlank { initial?.id ?: "P-${System.currentTimeMillis()}" },
                            name  = name.trim(),
                            phone = fullPhone.trim()
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text(if (isEdit) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyPeopleState(onAdd: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Person, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Text("No contacts yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Add contacts to link loans with people\nand send WhatsApp / SMS reminders",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onAdd) { Text("Add First Contact") }
    }
}
