package app.fynlo.logic

import app.fynlo.data.model.Transaction

fun Transaction.isGeneratedJournalEntry(): Boolean =
    tags.split(',')
        .map { it.trim().lowercase() }
        .any { it == "journal_only" }
