package com.example.cashmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val date: String = "",   // yyyy-MM-dd
    val netWorth: Double = 0.0,
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val projectId: String = "personal"
)