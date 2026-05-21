package app.fynlo.data.remote

import app.fynlo.data.model.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * Mirrors every Room write to Firestore.
 * Structure: users/{uid}/{collection}/{docId}
 *
 * All models are @Serializable, so we convert them via kotlinx.serialization
 * to Map<String, Any> which Firestore accepts natively.
 */
class FirestoreRepository(private val userId: String) {

    private val db = Firebase.firestore

    // ── Collection references ─────────────────────────────────────────────────

    private fun col(name: String) =
        db.collection("users").document(userId).collection(name)

    // ── Generic helpers ───────────────────────────────────────────────────────

    /** Converts any @Serializable object to a Map Firestore can store. */
    private inline fun <reified T> T.toFirestoreMap(): Map<String, Any?> {
        val json  = Json.encodeToString(this)
        val elem  = Json.parseToJsonElement(json).jsonObject
        return elem.mapValues { (_, v) -> v.toAny() }
    }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull       -> null
        is JsonPrimitive  -> when {
            isString      -> content
            booleanOrNull != null -> boolean
            longOrNull    != null -> long
            else          -> double
        }
        is JsonArray      -> map { it.toAny() }
        is JsonObject     -> mapValues { it.value.toAny() }
    }

    // ── Borrowers ─────────────────────────────────────────────────────────────

    suspend fun setBorrower(b: Borrower)   { col("borrowers").document(b.id).set(b.toFirestoreMap()).await() }
    suspend fun deleteBorrower(id: String) { col("borrowers").document(id).delete().await() }
    suspend fun deletePayment(id: String)  { col("payments").document(id).delete().await() }
    suspend fun deleteDebtPayment(id: String) { col("debt_payments").document(id).delete().await() }

    // ── Payments ──────────────────────────────────────────────────────────────

    suspend fun setPayment(p: Payment)     { col("payments").document(p.id).set(p.toFirestoreMap()).await() }

    // ── Debt Payments ─────────────────────────────────────────────────────────

    suspend fun setDebtPayment(p: DebtPayment) { col("debt_payments").document(p.id).set(p.toFirestoreMap()).await() }

    // ── Accounts ─────────────────────────────────────────────────────────────

    suspend fun setAccount(a: Account)         { col("accounts").document(a.id).set(a.toFirestoreMap()).await() }

    // ── Transactions ─────────────────────────────────────────────────────────

    suspend fun setTransaction(t: Transaction)     { col("transactions").document(t.id).set(t.toFirestoreMap()).await() }
    suspend fun deleteTransaction(id: String)      { col("transactions").document(id).delete().await() }

    // ── Investments ───────────────────────────────────────────────────────────

    suspend fun setInvestment(i: Investment)       { col("investments").document(i.id).set(i.toFirestoreMap()).await() }
    suspend fun deleteInvestment(id: String)       { col("investments").document(id).delete().await() }

    // ── Investment Valuations ────────────────────────────────────────────────

    suspend fun setValuation(v: InvestmentValuation) { col("investment_valuations").document(v.id).set(v.toFirestoreMap()).await() }

    // ── Debts ─────────────────────────────────────────────────────────────────

    suspend fun setDebt(d: Debt)           { col("debts").document(d.id).set(d.toFirestoreMap()).await() }
    suspend fun deleteDebt(id: String)     { col("debts").document(id).delete().await() }

    // ── People ────────────────────────────────────────────────────────────────

    suspend fun setPerson(p: Person)       { col("people").document(p.id).set(p.toFirestoreMap()).await() }
    suspend fun deletePerson(id: String)   { col("people").document(id).delete().await() }

    // ── Budgets ───────────────────────────────────────────────────────────────

    suspend fun setBudget(b: Budget)       { col("budgets").document(b.category).set(b.toFirestoreMap()).await() }
    suspend fun deleteBudget(cat: String)  { col("budgets").document(cat).delete().await() }

    // ── Goals ─────────────────────────────────────────────────────────────────

    suspend fun setGoal(g: Goal)           { col("goals").document(g.id).set(g.toFirestoreMap()).await() }
    suspend fun deleteGoal(id: String)     { col("goals").document(id).delete().await() }

    // ── Projects ──────────────────────────────────────────────────────────────

    suspend fun setProject(p: Project)     { col("projects").document(p.id).set(p.toFirestoreMap()).await() }
    suspend fun deleteProject(id: String)  { col("projects").document(id).delete().await() }

    // ── Recurring transactions ─────────────────────────────────────────────────

    suspend fun setRecurring(r: app.fynlo.data.model.RecurringTransaction) {
        col("recurring_transactions").document(r.id).set(r.toFirestoreMap()).await()
    }
    suspend fun deleteRecurring(id: String) {
        col("recurring_transactions").document(id).delete().await()
    }
}