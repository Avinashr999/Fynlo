package com.example.cashmemo

import android.app.Application
import androidx.room.Room
import com.example.cashmemo.data.FinanceRepository
import com.example.cashmemo.data.local.CashMemoDatabase

class CashMemoApplication : Application() {
    val database: CashMemoDatabase by lazy {
        Room.databaseBuilder(this, CashMemoDatabase::class.java, "cashmemo_database")
            .fallbackToDestructiveMigration()
            .build()
    }
    
    val repository: FinanceRepository by lazy {
        FinanceRepository(database.dao(), database)
    }
}