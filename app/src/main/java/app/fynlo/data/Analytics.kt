package app.fynlo.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralized analytics wrapper for Firebase Analytics.
 * Tracks screen views, feature usage, onboarding, and key user actions.
 */
object Analytics {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    // ── Screen Views ─────────────────────────────────────────────────────────

    fun screenView(screenName: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    // ── Onboarding & Setup ───────────────────────────────────────────────────

    fun onboardingComplete() {
        firebaseAnalytics?.logEvent("onboarding_complete", null)
    }

    fun setupStepComplete(step: Int, stepName: String) {
        firebaseAnalytics?.logEvent("setup_step_complete", Bundle().apply {
            putInt("step_number", step)
            putString("step_name", stepName)
        })
    }

    fun setupComplete() {
        firebaseAnalytics?.logEvent("setup_complete", null)
    }

    fun setupSkipped(atStep: Int) {
        firebaseAnalytics?.logEvent("setup_skipped", Bundle().apply {
            putInt("skipped_at_step", atStep)
        })
    }

    // ── Feature Usage ────────────────────────────────────────────────────────

    fun transactionAdded(type: String, category: String) {
        firebaseAnalytics?.logEvent("transaction_added", Bundle().apply {
            putString("transaction_type", type)
            putString("category", category)
        })
    }

    fun loanCreated(hasInterest: Boolean) {
        firebaseAnalytics?.logEvent("loan_created", Bundle().apply {
            putBoolean("has_interest", hasInterest)
        })
    }

    fun debtCreated() {
        firebaseAnalytics?.logEvent("debt_created", null)
    }

    fun investmentCreated(type: String) {
        firebaseAnalytics?.logEvent("investment_created", Bundle().apply {
            putString("investment_type", type)
        })
    }

    fun paymentCollected() {
        firebaseAnalytics?.logEvent("payment_collected", null)
    }

    fun dataExported(format: String) {
        firebaseAnalytics?.logEvent("data_exported", Bundle().apply {
            putString("format", format)
        })
    }

    fun signIn(method: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    // ── User Properties ──────────────────────────────────────────────────────

    fun setUserCurrency(currency: String) {
        firebaseAnalytics?.setUserProperty("default_currency", currency)
    }

    fun setUserLanguage(language: String) {
        firebaseAnalytics?.setUserProperty("app_language", language)
    }

    fun setAccountCount(count: Int) {
        firebaseAnalytics?.setUserProperty("account_count", count.toString())
    }
}
