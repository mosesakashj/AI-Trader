package com.example.trading

import com.example.broker.BrokerAdapter
import com.example.data.repositories.TradingRepository
import com.example.domain.model.LogLevel
import com.example.domain.model.Position

sealed class ReconciliationResult {
    object InSync : ReconciliationResult()
    data class Discrepancy(val message: String, val localPositions: List<Position>, val brokerPositions: List<Position>) : ReconciliationResult()
    data class Error(val error: String) : ReconciliationResult()
}

class PositionReconciler(
    private val repository: TradingRepository,
    private val brokerAdapter: BrokerAdapter
) {

    suspend fun reconcile(): ReconciliationResult {
        return try {
            val localPositions = repository.getOpenPositions()
            val brokerPositions = brokerAdapter.getPositions()

            val localIds = localPositions.map { it.id }.toSet()
            val brokerIds = brokerPositions.map { it.id }.toSet()

            if (localIds == brokerIds && localPositions.size == brokerPositions.size) {
                repository.logEvent(
                    level = LogLevel.INFO,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_SUCCESS",
                    message = "Position reconciliation successful: ${localPositions.size} active positions in sync"
                )
                ReconciliationResult.InSync
            } else {
                val diffMsg = "Position mismatch! Local DB count: ${localPositions.size}, Broker count: ${brokerPositions.size}"
                repository.logEvent(
                    level = LogLevel.CRITICAL,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_MISMATCH",
                    message = diffMsg
                )
                ReconciliationResult.Discrepancy(diffMsg, localPositions, brokerPositions)
            }
        } catch (e: Exception) {
            val errMsg = "Reconciliation exception: ${e.localizedMessage}"
            repository.logEvent(
                level = LogLevel.ERROR,
                component = "PositionReconciler",
                event = "RECONCILIATION_ERROR",
                message = errMsg
            )
            ReconciliationResult.Error(errMsg)
        }
    }
}
