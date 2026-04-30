package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // Bank, Cash, UPI, Trading
    val balance: Double,
    val icon: String = "",
    val color: String = "#3b82f6",
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
