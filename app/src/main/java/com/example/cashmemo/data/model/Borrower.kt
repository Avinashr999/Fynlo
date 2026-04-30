package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "borrowers")
data class Borrower(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val guarantor: String = "",
    val amount: Double,
    val rate: Double,
    val date: String,
    val due: String = "",
    val tenure: Int = 0,
    val type: String = "Simple Interest", // Simple Interest, Both, Compound Interest
    val paid: Double = 0.0,
    val status: String = "Active", // Active, Overdue, Cleared
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
