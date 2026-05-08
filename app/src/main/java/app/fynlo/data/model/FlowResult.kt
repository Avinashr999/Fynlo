package app.fynlo.data.model

/**
 * Output of the Smart Flow Wizard.
 * One FlowResult may create multiple records atomically:
 * e.g. a Lending flow creates both a Borrower and a Transaction.
 */
data class FlowResult(
    val eventType: String,          // "Received" | "Spent" | "Moved" | "Lent" | "Borrowed"
    val amount: Double,
    val description: String,
    val fromAccount: String = "",   // source account name
    val toAccount: String = "",     // destination account name
    val category: String = "",
    val date: String = "",
    val personName: String = "",    // for lending / debt
    val personPhone: String = "",
    val notes: String = "",
    val projectId: String = "personal"
)