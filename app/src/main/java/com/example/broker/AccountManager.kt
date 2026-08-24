package com.example.broker

import android.util.Log
import com.example.data.firestore.FirestoreRepository
import com.example.domain.model.BrokerAccount
import com.example.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountManager(
    private val repository: FirestoreRepository,
    private val secureStorage: SecureStorage
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _accounts = MutableStateFlow<List<BrokerAccount>>(emptyList())
    val accounts: StateFlow<List<BrokerAccount>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<BrokerAccount?>(null)
    val activeAccount: StateFlow<BrokerAccount?> = _activeAccount.asStateFlow()

    suspend fun initialize() {
        val saved = repository.getBrokerAccounts()
        _accounts.value = saved

        if (saved.isEmpty()) {
            migrateOrSeedDefaults()
            return
        }

        val active = saved.find { it.isActive } ?: saved.firstOrNull()
        if (active != null) {
            _activeAccount.value = active
            applyCredentials(active)
        }
    }

    private fun migrateOrSeedDefaults() {
        val server = secureStorage.getBrokerServer()
        val accountId = secureStorage.getBrokerAccountId()
        val password = secureStorage.getBrokerPassword()
        val gatewayUrl = secureStorage.getBrokerGatewayUrl()
        val apiKey = secureStorage.getBrokerApiKey()

        if (accountId.isNotBlank()) {
            val isDemo = server.contains("Trial", ignoreCase = true) || server.contains("Demo", ignoreCase = true)
            val migrated = BrokerAccount(
                label = if (isDemo) "Exness MT5 Demo" else "Exness MT5 Real",
                server = server,
                accountId = accountId,
                password = password,
                gatewayUrl = gatewayUrl,
                apiKey = apiKey,
                isActive = true
            )
            scope.launch {
                addAccount(migrated)
            }
        } else {
            // Seed default Exness Demo and Real accounts
            val defaultDemo = BrokerAccount(
                id = "default_exness_demo",
                label = "Exness MT5 Demo #14289052",
                server = "Exness-MT5Trial",
                accountId = "14289052",
                password = "",
                gatewayUrl = "",
                apiKey = "",
                isActive = true
            )
            val defaultReal = BrokerAccount(
                id = "default_exness_real",
                label = "Exness MT5 Real #18934211",
                server = "Exness-MT5Real",
                accountId = "18934211",
                password = "",
                gatewayUrl = "",
                apiKey = "",
                isActive = false
            )
            scope.launch {
                addAccount(defaultDemo)
                addAccount(defaultReal)
            }
        }
    }

    suspend fun addAccount(account: BrokerAccount): BrokerAccount {
        val toSave = if (_accounts.value.isEmpty()) account.copy(isActive = true) else account
        repository.saveBrokerAccount(toSave)

        val updated = _accounts.value + toSave
        _accounts.value = updated

        if (toSave.isActive) {
            switchToAccount(toSave.id)
        }
        return toSave
    }

    suspend fun removeAccount(accountId: String) {
        repository.deleteBrokerAccount(accountId)
        val updated = _accounts.value.filter { it.id != accountId }
        _accounts.value = updated

        if (_activeAccount.value?.id == accountId) {
            val newActive = updated.firstOrNull()
            if (newActive != null) {
                switchToAccount(newActive.id)
            } else {
                _activeAccount.value = null
                clearCredentials()
            }
        }
    }

    suspend fun switchToAccount(accountId: String) {
        val account = _accounts.value.find { it.id == accountId } ?: return

        val updated = _accounts.value.map {
            it.copy(isActive = it.id == accountId)
        }
        _accounts.value = updated
        _activeAccount.value = account

        repository.saveBrokerAccount(account.copy(isActive = true))
        updated.filter { it.id != accountId && it.isActive }.forEach {
            repository.saveBrokerAccount(it.copy(isActive = false))
        }

        applyCredentials(account)
    }

    private fun applyCredentials(account: BrokerAccount) {
        secureStorage.saveBrokerServer(account.server)
        secureStorage.saveBrokerAccountId(account.accountId)
        secureStorage.saveBrokerPassword(account.password)
        secureStorage.saveBrokerGatewayUrl(account.gatewayUrl)
        secureStorage.saveBrokerApiKey(account.apiKey)
    }

    private fun clearCredentials() {
        secureStorage.saveBrokerServer("Exness-MT5Real")
        secureStorage.saveBrokerAccountId("")
        secureStorage.saveBrokerPassword("")
        secureStorage.saveBrokerGatewayUrl("")
        secureStorage.saveBrokerApiKey("")
    }

    fun getActiveAccountBlocking(): BrokerAccount? = _activeAccount.value

    companion object {
        private const val TAG = "AccountManager"
    }
}
