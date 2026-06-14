package app.fynlo.data

import app.fynlo.data.model.BackupData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class BackupRestorePreview(
    val data: BackupData,
    val counts: List<Pair<String, Int>>,
    val warnings: List<String>,
) {
    val totalItems: Int get() = counts.sumOf { it.second }
}

object BackupRestorePreviewer {
    fun preview(json: String): BackupRestorePreview {
        val trimmed = json.trimStart()
        if (!trimmed.startsWith("{")) {
            throw IllegalStateException(
                "This file is not a restore backup. Pick a JSON backup or encrypted .fynloenc file created by Export JSON Backup."
            )
        }
        val raw = try {
            Json.decodeFromString<BackupData>(json)
        } catch (e: SerializationException) {
            throw IllegalStateException(
                "This file is not a valid Fynlo backup. Use Export JSON Backup for restore; CSV and PDF exports cannot be restored.",
                e,
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "This file is not a valid Fynlo backup. Use Export JSON Backup for restore; CSV and PDF exports cannot be restored.",
                e,
            )
        }
        when (val verdict = BackupIntegrity.check(raw)) {
            is BackupIntegrity.Check.Ok -> Unit
            is BackupIntegrity.Check.UnsupportedVersion ->
                throw IllegalStateException(
                    "Backup format v${verdict.version} is newer than this app supports. Update Fynlo and try again."
                )
            is BackupIntegrity.Check.HashMismatch ->
                throw IllegalStateException("Backup integrity check failed. The file may be corrupted or modified.")
        }

        val counts = listOf(
            "Projects" to raw.projects.size,
            "Accounts" to raw.accounts.size,
            "Transactions" to raw.transactions.size,
            "Loans" to raw.borrowers.size,
            "Debts" to raw.debts.size,
            "Investments" to raw.investments.size,
            "People" to raw.people.size,
            "Payments" to (raw.payments.size + raw.debtPayments.size),
            "Budgets" to raw.budgets.size,
            "Goals" to raw.goals.size,
            "Recurring" to raw.recurringTransactions.size,
        )

        return BackupRestorePreview(
            data = raw,
            counts = counts,
            warnings = validate(raw),
        )
    }

    private fun validate(data: BackupData): List<String> {
        val warnings = mutableListOf<String>()
        val projectIds = data.projects.map { it.id }.toSet() + "personal"
        val accountIds = data.accounts.map { it.id }.filter { it.isNotBlank() }.toSet()
        val accountNames = data.accounts.map { it.name }.filter { it.isNotBlank() }.toSet()
        val peopleIds = data.people.map { it.id }.filter { it.isNotBlank() }.toSet()

        fun warnMissingProjects(label: String, ids: List<String>) {
            val missing = ids.filter { it.isNotBlank() && it !in projectIds }.distinct()
            if (missing.isNotEmpty()) warnings += "$label reference missing projects: ${missing.take(3).joinToString()}"
        }

        warnMissingProjects("Transactions", data.transactions.map { it.projectId })
        warnMissingProjects("Accounts", data.accounts.map { it.projectId })
        warnMissingProjects("Loans", data.borrowers.map { it.projectId })
        warnMissingProjects("Debts", data.debts.map { it.projectId })
        warnMissingProjects("Investments", data.investments.map { it.projectId })
        warnMissingProjects("People", data.people.map { it.projectId })
        warnMissingProjects("Budgets", data.budgets.map { it.projectId })
        warnMissingProjects("Goals", data.goals.map { it.projectId })
        warnMissingProjects("Recurring", data.recurringTransactions.map { it.projectId })

        val missingAccountIds = buildList {
            data.transactions.forEach {
                if (it.fromAcctId.isNotBlank() && it.fromAcctId !in accountIds) add(it.fromAcctId)
                if (it.toAcctId.isNotBlank() && it.toAcctId !in accountIds) add(it.toAcctId)
            }
            data.recurringTransactions.forEach {
                if (it.fromAcctId.isNotBlank() && it.fromAcctId !in accountIds) add(it.fromAcctId)
                if (it.toAcctId.isNotBlank() && it.toAcctId !in accountIds) add(it.toAcctId)
            }
        }.distinct()
        if (missingAccountIds.isNotEmpty()) {
            warnings += "Some account ID links are missing. Fynlo will fall back to stored account names where possible."
        }

        val missingAccountNames = buildList {
            data.transactions.forEach {
                if (it.fromAcct.isNotBlank() && it.fromAcct !in accountNames) add(it.fromAcct)
                if (it.toAcct.isNotBlank() && it.toAcct !in accountNames) add(it.toAcct)
            }
            data.borrowers.forEach {
                if (it.sourceAccount.isNotBlank() && it.sourceAccount !in accountNames) add(it.sourceAccount)
            }
            data.recurringTransactions.forEach {
                if (it.fromAcct.isNotBlank() && it.fromAcct !in accountNames) add(it.fromAcct)
                if (it.toAcct.isNotBlank() && it.toAcct !in accountNames) add(it.toAcct)
            }
        }.distinct()
        if (missingAccountNames.isNotEmpty()) {
            warnings += "Some stored account names are missing from the backup: ${missingAccountNames.take(3).joinToString()}"
        }

        val missingPeopleIds = (data.borrowers.map { it.peopleId } + data.debts.map { it.peopleId })
            .filter { it.isNotBlank() && it !in peopleIds }
            .distinct()
        if (missingPeopleIds.isNotEmpty()) {
            warnings += "Some loan/debt contact links are missing. Names and phone numbers will still restore."
        }

        if (data.totalItemCount() == 0) {
            warnings += "This backup appears to contain no financial records."
        }

        return warnings
    }

    private fun BackupData.totalItemCount(): Int =
        accounts.size + transactions.size + borrowers.size + investments.size + debts.size +
            people.size + projects.size + payments.size + debtPayments.size + budgets.size +
            goals.size + recurringTransactions.size
}
