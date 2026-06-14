package app.fynlo.logic

/**
 * 3.2.80 — EMI affordability check.
 *
 * Given a monthly EMI and net monthly income, returns a burden percentage
 * and a categorical verdict. Thresholds follow the conservative-lender
 * convention used in Indian retail banking:
 *
 *   - **< 30%**  → Comfortable (industry-recommended cap)
 *   - **30–40%** → Manageable
 *   - **40–55%** → Stretched (banks may still approve but flag risk)
 *   - **≥ 55%**  → Risky (high default probability; consider reducing principal or extending tenure)
 *
 * Pure-Kotlin; covered by `EmiAffordabilityDataIntegrityTest`.
 */
object EmiAffordability {

    enum class Verdict { COMFORTABLE, MANAGEABLE, STRETCHED, RISKY, INVALID_INPUT }

    data class Assessment(
        val burdenPct: Double,
        val verdict: Verdict,
        val label: String,
        val explanation: String,
    )

    fun assess(monthlyEmi: Double, netMonthlyIncome: Double): Assessment {
        if (netMonthlyIncome <= 0 || monthlyEmi < 0) {
            return Assessment(
                burdenPct   = 0.0,
                verdict     = Verdict.INVALID_INPUT,
                label       = "—",
                explanation = "Enter a positive income to check affordability.",
            )
        }
        val pct = monthlyEmi / netMonthlyIncome * 100
        return when {
            pct < 30  -> Assessment(pct, Verdict.COMFORTABLE,
                "Comfortable",
                "EMI fits well within the 30% safe limit. You should have room for savings and unexpected expenses.")
            pct < 40  -> Assessment(pct, Verdict.MANAGEABLE,
                "Manageable",
                "EMI is within healthy bounds but leaves less room for emergencies. Keep a 3-month buffer.")
            pct < 55  -> Assessment(pct, Verdict.STRETCHED,
                "Stretched",
                "EMI is approaching the upper limit lenders consider safe. A drop in income or unexpected expense could be hard to absorb.")
            else      -> Assessment(pct, Verdict.RISKY,
                "Risky",
                "EMI is well above safe limits. Consider reducing principal or extending tenure to bring this below 40%.")
        }
    }
}
