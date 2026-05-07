package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Person
import java.util.*

@Composable
fun PeopleScreen(viewModel: FinanceViewModel) {
    val people by viewModel.people.collectAsState()
    var showAddDialog   by remember { mutableStateOf(false) }
    var editingPerson   by remember { mutableStateOf<Person?>(null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                Text("Contact Book",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp))
                Text("Contacts are used to link loans and debts to people.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
            }

            if (people.isEmpty()) {
                item { EmptyPeopleState(onAdd = { showAddDialog = true }) }
            } else {
                items(people) { person ->
                    PersonCard(
                        person   = person,
                        onEdit   = { editingPerson = person },
                        onDelete = { viewModel.deletePerson(person) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, contentDescription = "Add Contact") }
    }
}

@Composable
fun PersonCard(person: Person, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(person.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(person.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (person.id.isNotBlank()) {
                        Text("ID: ${person.id}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (person.phone.isNotBlank()) {
                        Text("ðŸ“ž ${person.phone}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No phone â€” tap edit to add", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonDialog(initial: Person? = null, onDismiss: () -> Unit, onConfirm: (Person) -> Unit) {
    val isEdit = initial != null
    var name  by remember { mutableStateOf(initial?.name  ?: "") }
    var id    by remember { mutableStateOf(initial?.id    ?: "P-${(100..999).random()}") }

    // Country codes list
    val countryCodes = remember { listOf(
        "+91 ðŸ‡®ðŸ‡³ India", "+1 ðŸ‡ºðŸ‡¸ USA/Canada", "+44 ðŸ‡¬ðŸ‡§ UK",
        "+61 ðŸ‡¦ðŸ‡º Australia", "+971 ðŸ‡¦ðŸ‡ª UAE", "+65 ðŸ‡¸ðŸ‡¬ Singapore",
        "+60 ðŸ‡²ðŸ‡¾ Malaysia", "+966 ðŸ‡¸ðŸ‡¦ Saudi Arabia", "+974 ðŸ‡¶ðŸ‡¦ Qatar",
        "+968 ðŸ‡´ðŸ‡² Oman", "+973 ðŸ‡§ðŸ‡­ Bahrain", "+49 ðŸ‡©ðŸ‡ª Germany",
        "+33 ðŸ‡«ðŸ‡· France", "+81 ðŸ‡¯ðŸ‡µ Japan", "+86 ðŸ‡¨ðŸ‡³ China"
    )}
    // Parse existing phone into prefix + number
    val existingPhone = initial?.phone ?: ""
    val initialPrefix = remember {
        countryCodes.find { existingPhone.startsWith(it.substringBefore(" ")) }
            ?: "+91 ðŸ‡®ðŸ‡³ India"
    }
    val initialNumber = remember {
        val code = initialPrefix.substringBefore(" ")
        if (existingPhone.startsWith(code)) existingPhone.removePrefix(code).trim() else existingPhone
    }
    var selectedCode  by remember { mutableStateOf(initialPrefix) }
    var phoneNumber   by remember { mutableStateOf(initialNumber) }
    var codeExpanded  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Contact" else "Add New Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Country code + phone number row
                Text("Phone Number (for WhatsApp/SMS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Country code dropdown
                    ExposedDropdownMenuBox(
                        expanded = codeExpanded,
                        onExpandedChange = { codeExpanded = !codeExpanded },
                        modifier = Modifier.width(130.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCode.substringBefore(" ") + " " +
                                    selectedCode.substringAfter(" ").substringBefore(" "),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codeExpanded) },
                            modifier = Modifier
                                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                .width(130.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = codeExpanded,
                            onDismissRequest = { codeExpanded = false }
                        ) {
                            countryCodes.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { selectedCode = code; codeExpanded = false }
                                )
                            }
                        }
                    }
                    // Phone number input
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("9876543210") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Preview full number
                if (phoneNumber.isNotBlank()) {
                    val fullNumber = selectedCode.substringBefore(" ") + phoneNumber
                    Text("Full number: $fullNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

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
            val fullPhone = if (phoneNumber.isNotBlank())
                selectedCode.substringBefore(" ") + phoneNumber else ""
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(
                    Person(id.ifBlank { initial?.id ?: "P-${System.currentTimeMillis()}" },
                        name.trim(), fullPhone.trim())) },
                enabled = name.isNotBlank()
            ) { Text(if (isEdit) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EmptyPeopleState(onAdd: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Person, null, Modifier.size(64.dp), tint = Color.LightGray)
        Text("No contacts yet", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Text("Add contacts to link loans with people\nand send WhatsApp/SMS reminders",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAdd) { Text("Add First Contact") }
    }
}






