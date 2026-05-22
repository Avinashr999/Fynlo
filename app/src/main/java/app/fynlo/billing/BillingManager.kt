package app.fynlo.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around Google Play Billing. Dormant while
 * [FeatureFlags.BILLING_ENABLED] is false — [init] no-ops and [isPro] stays true,
 * so nothing is gated. Once products are live and the flag is on, it queries the
 * subscription + lifetime products, restores entitlement, and runs the purchase flow.
 */
object BillingManager {

    private var billingClient: BillingClient? = null

    private val _isPro = MutableStateFlow(!FeatureFlags.BILLING_ENABLED)
    /** True when the user owns Pro — or always true while billing is disabled. */
    val isPro: StateFlow<Boolean> = _isPro

    private val _subProduct = MutableStateFlow<ProductDetails?>(null)
    val subProduct: StateFlow<ProductDetails?> = _subProduct

    private val _lifetimeProduct = MutableStateFlow<ProductDetails?>(null)
    val lifetimeProduct: StateFlow<ProductDetails?> = _lifetimeProduct

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    fun init(context: Context) {
        if (!FeatureFlags.BILLING_ENABLED) return  // dormant — no Play calls
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        connect()
    }

    private fun connect() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    refreshPurchases()
                }
            }
            override fun onBillingServiceDisconnected() { /* re-connects lazily on next call */ }
        })
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProProducts.SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()
        ) { _, list -> _subProduct.value = list.firstOrNull() }

        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProProducts.LIFETIME_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        ) { _, list -> _lifetimeProduct.value = list.firstOrNull() }
    }

    /** Re-check what the user owns (call on launch / "Restore purchases"). */
    fun refreshPurchases() {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { _, subs ->
            val subActive = subs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { _, inapps ->
                val lifetime = inapps.any {
                    it.products.contains(ProProducts.LIFETIME_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _isPro.value = subActive || lifetime
                (subs + inapps).forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        _isPro.value = true
        if (!purchase.isAcknowledged) {
            billingClient?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build()
            ) { /* acknowledged */ }
        }
    }

    /** Start the subscription purchase flow for a base plan (monthly/annual). */
    fun launchSubscription(activity: Activity, basePlanId: String) {
        val details = _subProduct.value ?: return
        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId }
            ?: details.subscriptionOfferDetails?.firstOrNull()
            ?: return
        billingClient?.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            ).build()
        )
    }

    /** Start the one-time lifetime purchase flow. */
    fun launchLifetime(activity: Activity) {
        val details = _lifetimeProduct.value ?: return
        billingClient?.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            ).build()
        )
    }
}
