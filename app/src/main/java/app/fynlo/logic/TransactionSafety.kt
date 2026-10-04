package app.fynlo.logic

import app.fynlo.data.model.Transaction

fun Transaction.isGeneratedJournalEntry(): Boolean =
    tags.split(',')
        .map { it.trim().lowercase() }
        .any { it == "journal_only" }

fun Transaction.isLinkedRepayment(): Boolean =
    ref.isNotBlank() && category in setOf("Loan Repayment", "Debt Repayment")

/** History edits must not reconstruct a payment by amount/date or guess its purpose. */
fun repaymentMetadataEdit(old: Transaction, updated: Transaction): Transaction? {
    if (!old.isLinkedRepayment() && !updated.isLinkedRepayment()) return null
    val metadataOnly = old.copy(desc = updated.desc, notes = updated.notes, updatedAt = updated.updatedAt)
    require(old.isLinkedRepayment() && updated == metadataOnly) {
        "This is a loan or debt payment. Only its description and notes can be changed here."
    }
    return metadataOnly
}
