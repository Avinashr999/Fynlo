package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // Stocks, Mutual Funds, Gold, etc.
    val subtype: String = "",
    val invested: Double,
    val currentVal: Double,
    val date: String,
    val maturityDate: String = "",
    val rate: Double = 0.0,
    val realized: Double = 0.0,
    val withdrawn: Double = 0.0,
    val notes: String = ""
)