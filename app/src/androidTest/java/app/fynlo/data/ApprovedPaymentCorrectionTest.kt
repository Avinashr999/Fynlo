package app.fynlo.data

import androidx.test.platform.app.InstrumentationRegistry
import app.fynlo.data.model.Payment
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.di.AppModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Manual repair harness, skipped by all ordinary test runs. No owner data is compiled into it. */
class ApprovedPaymentCorrectionTest {
    @Serializable data class Request(val expected: List<Payment>, val corrected: List<Payment>)

    @Test fun applyExplicitlyApprovedClassificationCorrection() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("approvedPaymentCorrection") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.filesDir, "approved-payment-correction.json")
        check(file.isFile) { "Approved correction payload is required." }
        val request = Json { ignoreUnknownKeys = true }.decodeFromString<Request>(file.readText())
        val db = AppModule.provideDatabase(context)
        try {
            val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
            val remote = FirestoreRepository(uid)
            val repository = FinanceRepository(db.dao(), db, remote, SyncManager("", db.dao()))
            val accounts = db.dao().getAllAccountsList()
            val transactions = db.dao().getAllTransactionsList()
            val auditIds = db.dao().getAllAuditEventsOnce().map { it.id }.toSet()
            repository.correctBorrowerPaymentClassifications(request.expected, request.corrected)
            // Re-running the identical request must be harmless.
            repository.correctBorrowerPaymentClassifications(request.expected, request.corrected)
            assertEquals(accounts, db.dao().getAllAccountsList())
            assertEquals(transactions, db.dao().getAllTransactionsList())
            withTimeout(60_000) {
                for (expected in request.corrected) {
                    val saved = requireNotNull(db.dao().getPaymentById(expected.id))
                    assertEquals(expected, saved.copy(updatedAt = expected.updatedAt))
                    remote.setPayment(saved)
                    val doc = FirebaseFirestore.getInstance().collection("users").document(uid)
                        .collection("payments").document(saved.id).get(Source.SERVER).await()
                    assertEquals(saved.principal, doc.getDouble("principal")!!, 0.0)
                    assertEquals(saved.interest, doc.getDouble("interest")!!, 0.0)
                    assertEquals(saved.interestAllocationType, doc.getString("interestAllocationType"))
                }
                val borrower = requireNotNull(db.dao().getBorrowerById(request.expected.first().loanId))
                remote.setBorrower(borrower)
                db.dao().getAllAuditEventsOnce().filter { it.id !in auditIds }.forEach { remote.setAuditEvent(it) }
            }
        } finally {
            db.close()
        }
    }
}
