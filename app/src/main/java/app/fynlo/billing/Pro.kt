package app.fynlo.billing

/**
 * Master switches for the Pro / in-app-purchase system.
 *
 * While [BILLING_ENABLED] is false the whole app is unlocked (BillingManager.isPro
 * stays true) and the upgrade UI is hidden — so building this changes nothing for
 * users. Flip it to true only after the products below are live in Play Console.
 */
object FeatureFlags {
    const val BILLING_ENABLED = false
}

/** Product IDs — must match exactly what you create in Play Console. */
object ProProducts {
    const val SUBSCRIPTION_ID   = "fynlo_pro"      // subscription
    const val BASE_PLAN_MONTHLY = "monthly"        // base plan within fynlo_pro
    const val BASE_PLAN_ANNUAL  = "annual"         // base plan within fynlo_pro
    const val LIFETIME_ID       = "fynlo_lifetime" // one-time (non-consumable)
}
