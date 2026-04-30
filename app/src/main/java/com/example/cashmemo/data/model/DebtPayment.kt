package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debt_payments")
data class DebtPayment(
    @PrimaryKey val id: String,
    val debtId: String,
    val name: String,
    val date: String,
    val type: String,
    val amount: Double,
    val principal: Double = 0.0,
    val interest: Double = 0.0,
    val mode: String = "",
    val notes: String = ""
)