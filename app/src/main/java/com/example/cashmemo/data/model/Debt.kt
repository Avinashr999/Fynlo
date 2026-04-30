package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val type: String = "Friend / Family",
    val amount: Double,
    val rate: Double,
    val date: String,
    val due: String = "",
    val tenure: Int = 0,
    val intType: String = "Simple Interest",
    val paid: Double = 0.0,
    val status: String = "Active", // Active, Cleared, Overdue
    val collateral: String = "",
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
