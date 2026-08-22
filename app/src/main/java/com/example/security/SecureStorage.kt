package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("edgetrader_secure_prefs", Context.MODE_PRIVATE)
    private val keyStoreAlias = "EdgeTraderKeyAlias"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private var isKeyStoreAvailable = false

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (!keyStore.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance("AES", androidKeyStore)
                val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    keyStoreAlias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            isKeyStoreAvailable = true
        } catch (e: Throwable) {
            isKeyStoreAvailable = false
        }
    }

    private fun getSecretKey(): SecretKey? {
        if (!isKeyStoreAvailable) return null
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            keyStore.getKey(keyStoreAlias, null) as? SecretKey
        } catch (e: Throwable) {
            null
        }
    }

    fun saveEncryptedString(key: String, rawValue: String) {
        if (rawValue.isBlank()) {
            prefs.edit().remove(key).remove("${key}_iv").apply()
            return
        }
        val secretKey = getSecretKey()
        if (secretKey != null) {
            try {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(rawValue.toByteArray(Charsets.UTF_8))

                val encryptedB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

                prefs.edit()
                    .putString(key, encryptedB64)
                    .putString("${key}_iv", ivB64)
                    .apply()
                return
            } catch (e: Exception) {
                // Fall through to fallback
            }
        }
        // Fallback for JVM test environments without AndroidKeyStore
        prefs.edit().putString(key, Base64.encodeToString(rawValue.toByteArray(), Base64.NO_WRAP)).apply()
    }

    fun getDecryptedString(key: String): String {
        val encryptedB64 = prefs.getString(key, null) ?: return ""
        val ivB64 = prefs.getString("${key}_iv", null)
        val secretKey = getSecretKey()

        if (ivB64 != null && secretKey != null) {
            try {
                val cipher = Cipher.getInstance(transformation)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                // Fall through
            }
        }

        // Fallback decoding
        return runCatching { String(Base64.decode(encryptedB64, Base64.NO_WRAP)) }.getOrDefault("")
    }

    fun getTelegramToken(): String = getDecryptedString("telegram_bot_token")
    fun saveTelegramToken(token: String) = saveEncryptedString("telegram_bot_token", token)

    fun getTelegramChatId(): String = prefs.getString("telegram_chat_id", "") ?: ""
    fun saveTelegramChatId(chatId: String) = prefs.edit().putString("telegram_chat_id", chatId).apply()

    // Exness / Broker Account & Gateway Credentials
    fun getBrokerServer(): String = prefs.getString("broker_server", "Exness-MT5Real") ?: "Exness-MT5Real"
    fun saveBrokerServer(server: String) = prefs.edit().putString("broker_server", server).apply()

    fun getBrokerAccountId(): String = prefs.getString("broker_account_id", "") ?: ""
    fun saveBrokerAccountId(accId: String) = prefs.edit().putString("broker_account_id", accId).apply()

    fun getBrokerPassword(): String = getDecryptedString("broker_password")
    fun saveBrokerPassword(password: String) = saveEncryptedString("broker_password", password)

    fun getBrokerGatewayUrl(): String = prefs.getString("broker_gateway_url", "") ?: ""
    fun saveBrokerGatewayUrl(url: String) = prefs.edit().putString("broker_gateway_url", url).apply()

    fun getBrokerApiKey(): String = getDecryptedString("broker_api_key")
    fun saveBrokerApiKey(apiKey: String) = saveEncryptedString("broker_api_key", apiKey)

    // NVIDIA LLM API Key
    fun getNvidiaApiKey(): String = getDecryptedString("nvidia_api_key")
    fun saveNvidiaApiKey(apiKey: String) = saveEncryptedString("nvidia_api_key", apiKey)

    // Google Gemini API Key
    fun getGeminiApiKey(): String = getDecryptedString("gemini_api_key")
    fun saveGeminiApiKey(apiKey: String) = saveEncryptedString("gemini_api_key", apiKey)

    // Anthropic Claude API Key
    fun getClaudeApiKey(): String = getDecryptedString("claude_api_key")
    fun saveClaudeApiKey(apiKey: String) = saveEncryptedString("claude_api_key", apiKey)

    // OpenAI ChatGPT API Key
    fun getChatGptApiKey(): String = getDecryptedString("chatgpt_api_key")
    fun saveChatGptApiKey(apiKey: String) = saveEncryptedString("chatgpt_api_key", apiKey)

    fun clearAllSecrets() {
        prefs.edit().clear().apply()
    }
}
