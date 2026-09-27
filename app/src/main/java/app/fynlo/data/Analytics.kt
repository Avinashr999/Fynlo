package app.fynlo.data

import android.content.Context

/**
 * Personal-mode analytics facade.
 *
 * The app is now maintained for private use, so user-event telemetry is kept
 * as no-op methods. Existing call sites can stay simple without shipping
 * Firebase Analytics or doing extra runtime work.
 */
object Analytics {
    fun init(context: Context) = Unit

    // ── Screen Views ─────────────────────────────────────────────────────────

    fun screenView(screenName: String) = Unit

    // ── Onboarding & Setup ───────────────────────────────────────────────────

    fun onboardingComplete() = Unit

    fun setupStepComplete(step: Int, stepName: String) = Unit

    fun setupComplete() = Unit

    fun setupSkipped(atStep: Int) = Unit

    // ── Feature Usage ────────────────────────────────────────────────────────

    fun transactionAdded(type: String, category: String) = Unit

    fun loanCreated(hasInterest: Boolean) = Unit

    fun debtCreated() = Unit

    fun investmentCreated(type: String) = Unit

    fun paymentCollected() = Unit

    fun dataExported(format: String) = Unit

    fun signIn(method: String) = Unit

    // ── User Properties ──────────────────────────────────────────────────────

    fun setUserCurrency(currency: String) = Unit

    fun setUserLanguage(language: String) = Unit

    fun setAccountCount(count: Int) = Unit
}
