package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "people")
data class Person(
    @PrimaryKey val id: String, // Unique ID (e.g., P-001)
    val name: String,
    val phone: String = "",
    val type: String = "Individual", // Individual, Business, etc.
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
