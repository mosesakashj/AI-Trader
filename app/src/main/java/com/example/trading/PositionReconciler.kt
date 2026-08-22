package com.example.trading

import com.example.broker.BrokerAdapter
import com.example.data.firestore.FirestoreRepository
import com.example.domain.model.LogLevel
import com.example.domain.model.Position
import com.example.domain.model.TradeDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

sealed class ReconciliationResult {
    object InSync : ReconciliationResult()
    data class Discrepancy(
        val message: String,
        val localPositions: List<Position>,
        val brokerPositions: List<Position>,
        val unmatchedLocal: List<Position>,
        val unmatchedBroker: List<Position>
    ) : ReconciliationResult()
    data class Error(val error: String) : ReconciliationResult()
    data class Repaired(
        val message: String,
        val removedLocal: List<Position>,
        val addedFromBroker: List<Position>,
        val updatedLocal: List<Position>
    ) : ReconciliationResult()
}

class PositionReconciler(
    private val repository: FirestoreRepository,
    private val brokerAdapter: BrokerAdapter
) {

    private val positionMatchTolerance = 0.0001 // Price tolerance for matching

    suspend fun reconcile(): ReconciliationResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val localPositions = repository.getOpenPositions()
            val brokerPositions = brokerAdapter.getPositions()

            val matchResult = matchPositions(localPositions, brokerPositions)

            if (matchResult.unmatchedLocal.isEmpty() && matchResult.unmatchedBroker.isEmpty()) {
                repository.logEvent(
                    level = LogLevel.INFO,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_SUCCESS",
                    message = "Position reconciliation successful: ${localPositions.size} active positions in sync"
                )
                ReconciliationResult.InSync
            } else {
                val diffMsg = "Position mismatch! Local DB count: ${localPositions.size}, Broker count: ${brokerPositions.size}. " +
                    "Unmatched local: ${matchResult.unmatchedLocal.size}, Unmatched broker: ${matchResult.unmatchedBroker.size}"
                repository.logEvent(
                    level = LogLevel.CRITICAL,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_MISMATCH",
                    message = diffMsg
                )
                ReconciliationResult.Discrepancy(diffMsg, localPositions, brokerPositions, matchResult.unmatchedLocal, matchResult.unmatchedBroker)
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

    suspend fun reconcileAndRepair(): ReconciliationResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val localPositions = repository.getOpenPositions()
            val brokerPositions = brokerAdapter.getPositions()

            val matchResult = matchPositions(localPositions, brokerPositions)

            if (matchResult.unmatchedLocal.isEmpty() && matchResult.unmatchedBroker.isEmpty()) {
                repository.logEvent(
                    level = LogLevel.INFO,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_SUCCESS",
                    message = "Position reconciliation successful: ${localPositions.size} active positions in sync"
                )
                ReconciliationResult.InSync
            } else {
                // Attempt auto-repair
                val repairResult = autoRepair(matchResult)
                if (repairResult != null) {
                    repository.logEvent(
                        level = LogLevel.WARN,
                        component = "PositionReconciler",
                        event = "RECONCILIATION_REPAIRED",
                        message = "Auto-repair completed: Removed ${repairResult.removedLocal.size} stale local positions, " +
                            "Added ${repairResult.addedFromBroker.size} missing positions from broker, " +
                            "Updated ${repairResult.updatedLocal.size} existing positions"
                    )
                    return@withContext ReconciliationResult.Repaired(
                        "Auto-repair successful: Local DB synchronized with broker",
                        repairResult.removedLocal,
                        repairResult.addedFromBroker,
                        repairResult.updatedLocal
                    )
                }

                // Repair failed or not possible
                val diffMsg = "Position mismatch! Local DB count: ${localPositions.size}, Broker count: ${brokerPositions.size}. " +
                    "Unmatched local: ${matchResult.unmatchedLocal.size}, Unmatched broker: ${matchResult.unmatchedBroker.size}. " +
                    "Auto-repair attempted but could not resolve all discrepancies."
                repository.logEvent(
                    level = LogLevel.CRITICAL,
                    component = "PositionReconciler",
                    event = "RECONCILIATION_MISMATCH_UNREPAIRED",
                    message = diffMsg
                )
                ReconciliationResult.Discrepancy(diffMsg, localPositions, brokerPositions, matchResult.unmatchedLocal, matchResult.unmatchedBroker)
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

    private data class MatchResult(
        val matched: List<Pair<Position, Position>>, // local, broker
        val unmatchedLocal: List<Position>,
        val unmatchedBroker: List<Position>
    )

    private fun matchPositions(localPositions: List<Position>, brokerPositions: List<Position>): MatchResult {
        val matched = mutableListOf<Pair<Position, Position>>()
        val usedBrokerIndices = mutableSetOf<Int>()

        // First pass: Match by ID (primary key)
        localPositions.forEach { localPos ->
            val brokerIndex = brokerPositions.indexOfFirst { brokerPos ->
                brokerPos.id == localPos.id && !usedBrokerIndices.contains(brokerPositions.indexOf(brokerPos))
            }
            if (brokerIndex >= 0) {
                matched.add(localPos to brokerPositions[brokerIndex])
                usedBrokerIndices.add(brokerIndex)
            }
        }

        // Second pass: Match by attributes (symbol, direction, entry price) for unmatched
        val unmatchedLocalAfterId = localPositions.filter { lp ->
            matched.none { it.first.id == lp.id }
        }
        val unmatchedBrokerAfterId = brokerPositions.filterIndexed { idx, _ ->
            idx !in usedBrokerIndices
        }

        val usedBrokerAfterAttr = mutableSetOf<Int>()
        unmatchedLocalAfterId.forEach { localPos ->
            val brokerIdx = unmatchedBrokerAfterId.indexOfFirst { bp ->
                bp.symbol == localPos.symbol &&
                bp.direction == localPos.direction &&
                abs(bp.entryPrice - localPos.entryPrice) < positionMatchTolerance &&
                !usedBrokerAfterAttr.contains(unmatchedBrokerAfterId.indexOf(bp))
            }
            if (brokerIdx >= 0) {
                matched.add(localPos to unmatchedBrokerAfterId[brokerIdx])
                usedBrokerAfterAttr.add(brokerIdx)
            }
        }

        val finalMatchedLocalIds = matched.map { it.first.id }.toSet()
        val finalMatchedBrokerIds = matched.map { it.second.id }.toSet()

        val finalUnmatchedLocal = localPositions.filter { it.id !in finalMatchedLocalIds }
        val finalUnmatchedBroker = brokerPositions.filter { it.id !in finalMatchedBrokerIds }

        return MatchResult(matched, finalUnmatchedLocal, finalUnmatchedBroker)
    }

    private data class RepairResult(
        val removedLocal: List<Position>,
        val addedFromBroker: List<Position>,
        val updatedLocal: List<Position>
    )

    private suspend fun autoRepair(matchResult: MatchResult): RepairResult? {
        val removedLocal = mutableListOf<Position>()
        val addedFromBroker = mutableListOf<Position>()
        val updatedLocal = mutableListOf<Position>()

        // Remove stale local positions (exist in DB but not in broker)
        matchResult.unmatchedLocal.forEach { localPos ->
            repository.removePosition(localPos.id)
            removedLocal.add(localPos)
        }

        // Add missing positions from broker
        matchResult.unmatchedBroker.forEach { brokerPos ->
            // Use broker position ID as source of truth
            val position = brokerPos.copy(id = brokerPos.id)
            repository.recordPosition(position)
            addedFromBroker.add(position)
        }

        // Update matched positions with latest broker data (prices, P&L)
        matchResult.matched.forEach { (localPos, brokerPos) ->
            // Only update if prices/P&L differ significantly
            val priceChanged = abs(localPos.currentPrice - brokerPos.currentPrice) > positionMatchTolerance
            val profitChanged = abs(localPos.unrealizedProfit - brokerPos.unrealizedProfit) > 0.01
            val slTpChanged = localPos.stopLoss != brokerPos.stopLoss || localPos.takeProfit != brokerPos.takeProfit

            if (priceChanged || profitChanged || slTpChanged) {
                val updatedPos = localPos.copy(
                    currentPrice = brokerPos.currentPrice,
                    unrealizedProfit = brokerPos.unrealizedProfit,
                    unrealizedR = brokerPos.unrealizedR,
                    stopLoss = brokerPos.stopLoss,
                    takeProfit = brokerPos.takeProfit
                )
                repository.recordPosition(updatedPos)
                updatedLocal.add(updatedPos)
            }
        }

        return RepairResult(removedLocal, addedFromBroker, updatedLocal)
    }

    companion object {
        fun isRepairable(result: ReconciliationResult): Boolean {
            return when (result) {
                is ReconciliationResult.Discrepancy -> {
                    // Repairable if all unmatched broker positions can be added
                    // and all unmatched local can be removed (no manual intervention needed)
                    true
                }
                is ReconciliationResult.Repaired -> true
                else -> false
            }
        }
    }
}