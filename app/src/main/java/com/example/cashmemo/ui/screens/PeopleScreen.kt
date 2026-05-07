package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Text("📞 ${person.phone}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No phone — tap edit to add", style = MaterialTheme.typography.bodySmall,
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

@Composable
fun AddPersonDialog(initial: Person? = null, onDismiss: () -> Unit, onConfirm: (Person) -> Unit) {
    val isEdit = initial != null
    var name  by remember { mutableStateOf(initial?.name  ?: "") }
    var id    by remember { mutableStateOf(initial?.id    ?: "P-${(100..999).random()}") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }

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
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone Number (for WhatsApp/SMS)") },
                    singleLine = true,
                    placeholder = { Text("e.g. 9876543210") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                onClick = { if (name.isNotBlank()) onConfirm(Person(id.ifBlank { initial?.id ?: "P-${System.currentTimeMillis()}" }, name.trim(), phone.trim())) },
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