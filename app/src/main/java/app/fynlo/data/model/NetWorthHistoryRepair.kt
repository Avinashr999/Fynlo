package app.fynlo.data.model

import kotlinx.serialization.Serializable

/** Before-images make an approved history-only import fail closed if the phone changed. */
@Serializable
data class NetWorthHistoryRepair(
    val recovered: NetWorthSnapshot,
    val expectedOriginal: NetWorthSnapshot? = null,
)
