package com.example.cashmemo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val borrowers: List<Borrower>,
    val investments: List<Investment>,
    val debts: List<Debt>,
    val people: List<Person>
)