package app.fynlo.delegate

import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.FinancialSummary
import app.fynlo.data.model.Goal
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Person
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.AuditEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.OutputStream

class ExportDelegate(
    private val ctx: ViewModelContext,
    private val accountsFlow: StateFlow<List<Account>>,
    private val transactionsFlow: StateFlow<List<Transaction>>,
    private val borrowersFlow: StateFlow<List<Borrower>>,
    private val debtsFlow: StateFlow<List<Debt>>,
    private val investmentsFlow: StateFlow<List<Investment>>,
    private val peopleFlow: StateFlow<List<Person>>,
    private val budgetsFlow: StateFlow<List<Budget>>,
    private val goalsFlow: StateFlow<List<Goal>>,
    private val paymentsFlow: StateFlow<List<Payment>>,
    private val debtPaymentsFlow: StateFlow<List<DebtPayment>>,
    private val financialSummaryFlow: StateFlow<FinancialSummary>,
    private val auditEventsFlow: StateFlow<List<AuditEvent>>,
    private val currentProjectFlow: StateFlow<app.fynlo.data.model.Project?>
) {

    fun exportAuditTrailCsv(): String {
        fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return buildString {
            appendLine("Timestamp,Action,Entity Type,Entity Id,Title,Account,Amount Delta,Before,After,Reason")
            auditEventsFlow.value.forEach { event ->
                appendLine(
                    listOf(
                        dateFormat.format(java.util.Date(event.timestamp)),
                        event.action,
                        event.entityType,
                        event.entityId,
                        event.title,
                        event.accountName,
                        event.amountDelta.toString(),
                        event.beforeValue,
                        event.afterValue,
                        event.reason,
                    ).joinToString(",") { csvCell(it) }
                )
            }
        }
    }

    suspend fun exportAllData(): String {
        ctx.recalcCoordinator.runAndStamp()
        val json = ctx.repository.getAllDataAsJson()
        app.fynlo.data.Analytics.dataExported("json")
        return json
    }

    fun restoreData(json: String) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.restoreDataFromJson(json) }
    }

    suspend fun restoreDataNow(json: String) {
        ctx.repository.restoreDataFromJson(json)
    }

    suspend fun exportToCSV(): String {
        ctx.recalcCoordinator.runAndStamp()
        val csv = app.fynlo.logic.ExportUtility.generateCSV(
            transactionsFlow.value, borrowersFlow.value, investmentsFlow.value
        )
        app.fynlo.data.Analytics.dataExported("csv")
        return csv
    }

    suspend fun exportDataToCSV(scope: String): String {
        ctx.recalcCoordinator.runAndStamp()
        val csv = app.fynlo.logic.ExportUtility.generateDataExportCSV(
            scope = scope,
            accounts = accountsFlow.value,
            transactions = transactionsFlow.value,
            borrowers = borrowersFlow.value,
            debts = debtsFlow.value,
            investments = investmentsFlow.value,
            people = peopleFlow.value,
            budgets = budgetsFlow.value,
            goals = goalsFlow.value,
        )
        app.fynlo.data.Analytics.dataExported("csv_${scope.lowercase()}")
        return csv
    }

    suspend fun exportToPDF(
        outputStream: OutputStream,
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val recalcAt = ctx.recalcCoordinator.runAndStamp()
        val currencyCode = currentProjectFlow.value?.currency ?: "INR"
        app.fynlo.logic.ExportUtility.generatePDF(
            outputStream, financialSummaryFlow.value,
            transactionsFlow.value, borrowersFlow.value, investmentsFlow.value,
            lastRecalcAt = recalcAt,
            currencyCode = currencyCode,
            projectName = currentProjectFlow.value?.name ?: "Personal",
            userEmail   = app.fynlo.data.AuthManager().userEmail,
            periodLabel = "All time",
            debts       = debtsFlow.value,
            snapshots   = ctx.repository.getNetWorthSnapshots(ctx.currentProjectId()).first(),
            dateFormat  = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("pdf")
    }

    suspend fun exportDataToPDF(
        outputStream: OutputStream,
        scope: String,
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        ctx.recalcCoordinator.runAndStamp()
        app.fynlo.logic.ExportUtility.generateDataExportPDF(
            outputStream = outputStream,
            scope = scope,
            accounts = accountsFlow.value,
            transactions = transactionsFlow.value,
            borrowers = borrowersFlow.value,
            debts = debtsFlow.value,
            investments = investmentsFlow.value,
            people = peopleFlow.value,
            budgets = budgetsFlow.value,
            goals = goalsFlow.value,
            currencyCode = currentProjectFlow.value?.currency ?: "INR",
            projectName = currentProjectFlow.value?.name ?: "Personal",
            userEmail = app.fynlo.data.AuthManager().userEmail,
            dateFormat = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("pdf_${scope.lowercase()}")
    }

    suspend fun exportToXLSX(
        outputStream: OutputStream,
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val recalcAt = ctx.recalcCoordinator.runAndStamp()
        app.fynlo.logic.ExcelExportUtility.generateFullBackup(
            outputStream,
            accountsFlow.value,
            transactionsFlow.value,
            borrowersFlow.value,
            debtsFlow.value,
            investmentsFlow.value,
            paymentsFlow.value,
            debtPaymentsFlow.value,
            lastRecalcAt = recalcAt,
            summary      = financialSummaryFlow.value,
            currencyCode = currentProjectFlow.value?.currency ?: "INR",
            dateFormat   = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("xlsx")
    }
}
