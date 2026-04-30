package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "business",       // icon key for UI
    val color: String = "#3b82f6",       // hex color for UI chip
    val currency: String = "INR",
    val createdAt: String = "",
    val updatedAt: Long = 0L
)
