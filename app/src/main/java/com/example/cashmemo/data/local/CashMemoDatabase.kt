package com.example.cashmemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cashmemo.data.model.*

@Database(
    entities = [
        Borrower::class,
        Payment::class,
        Account::class,
        Transaction::class,
        Investment::class,
        Debt::class,
        DebtPayment::class,
        Person::class,
        Budget::class,
        Goal::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CashMemoDatabase : RoomDatabase() {
    abstract fun dao(): CashMemoDao
}