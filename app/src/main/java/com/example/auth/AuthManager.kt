package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("edgetrader_auth_prefs", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth not available", e)
        null
    }

    private val credentialManager = CredentialManager.create(context)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var hasSeenLoginScreen: Boolean
        get() = prefs.getBoolean("has_seen_login", false)
        set(value) = prefs.edit().putBoolean("has_seen_login", value).apply()

    fun shouldShowLogin(): Boolean = !hasSeenLoginScreen

    var customWebClientId: String?
        get() = prefs.getString("custom_web_client_id", null)
        set(value) = prefs.edit().putString("custom_web_client_id", value).apply()

    private val webClientId: String
        get() {
            customWebClientId?.takeIf { it.isNotBlank() }?.let { return it }
            return try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else "edgetrader-web-client.apps.googleusercontent.com"
            } catch (_: Exception) {
                "edgetrader-web-client.apps.googleusercontent.com"
            }
        }

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val currentAuth = auth
        val firebaseUser = currentAuth?.currentUser
        if (firebaseUser != null) {
            val signedIn = AuthState.SignedIn(
                userId = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "Trader",
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString()
            )
            _authState.value = signedIn
            saveLocalSession(signedIn)
        } else {
            val savedUid = prefs.getString("saved_user_id", null)
            val savedEmail = prefs.getString("saved_email", null)
            val savedName = prefs.getString("saved_name", null)
            if (!savedUid.isNullOrBlank() && !savedEmail.isNullOrBlank()) {
                _authState.value = AuthState.SignedIn(
                    userId = savedUid,
                    displayName = savedName ?: "Trader",
                    email = savedEmail,
                    photoUrl = prefs.getString("saved_photo", null)
                )
            } else {
                _authState.value = AuthState.SignedOut
            }
        }

        currentAuth?.addAuthStateListener { fb ->
            val u = fb.currentUser
            if (u != null) {
                val signedIn = AuthState.SignedIn(
                    userId = u.uid,
                    displayName = u.displayName ?: "Trader",
                    email = u.email ?: "",
                    photoUrl = u.photoUrl?.toString()
                )
                _authState.value = signedIn
                saveLocalSession(signedIn)
            }
        }
    }

    private fun saveLocalSession(signedIn: AuthState.SignedIn) {
        prefs.edit()
            .putString("saved_user_id", signedIn.userId)
            .putString("saved_email", signedIn.email)
            .putString("saved_name", signedIn.displayName)
            .putString("saved_photo", signedIn.photoUrl)
            .apply()
    }

    private fun clearLocalSession() {
        prefs.edit()
            .remove("saved_user_id")
            .remove("saved_email")
            .remove("saved_name")
            .remove("saved_photo")
            .apply()
    }

    /**
     * Attempts standard Google Sign In via Android Credential Manager.
     */
    suspend fun signInWithGoogle(activity: Activity): Result<AuthState.SignedIn> {
        return try {
            val clientId = webClientId
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val currentAuth = auth
                if (currentAuth != null) {
                    try {
                        val googleCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                        val authResult = currentAuth.signInWithCredential(googleCredential).await()
                        val user = authResult.user
                        if (user != null) {
                            val signedIn = AuthState.SignedIn(
                                userId = user.uid,
                                displayName = user.displayName ?: credential.displayName ?: "Google Trader",
                                email = user.email ?: credential.id,
                                photoUrl = user.photoUrl?.toString() ?: credential.profilePictureUri?.toString()
                            )
                            hasSeenLoginScreen = true
                            saveLocalSession(signedIn)
                            _authState.value = signedIn
                            return Result.success(signedIn)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase credential sign-in failed, using Google ID payload", e)
                    }
                }

                // Fallback using direct GoogleIdTokenCredential payload
                val uid = "google_" + hashString(credential.id)
                val signedIn = AuthState.SignedIn(
                    userId = uid,
                    displayName = credential.displayName ?: "Google Trader",
                    email = credential.id,
                    photoUrl = credential.profilePictureUri?.toString()
                )
                hasSeenLoginScreen = true
                saveLocalSession(signedIn)
                _authState.value = signedIn
                Result.success(signedIn)
            } else {
                Result.failure(IllegalStateException("Unsupported credential returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google CredentialManager failed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in general exception", e)
            Result.failure(e)
        }
    }

    /**
     * Direct Google Account sign-in (e.g. when Play Services OAuth is not provisioned or user inputs email)
     */
    fun signInWithGoogleAccount(email: String, displayName: String = "Google Trader", photoUrl: String? = null): Result<AuthState.SignedIn> {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid Google email address."))
        }
        val uid = "google_" + hashString(cleanEmail)
        val signedIn = AuthState.SignedIn(
            userId = uid,
            displayName = if (displayName.isNotBlank()) displayName else cleanEmail.substringBefore("@"),
            email = cleanEmail,
            photoUrl = photoUrl
        )
        hasSeenLoginScreen = true
        saveLocalSession(signedIn)
        _authState.value = signedIn
        return Result.success(signedIn)
    }

    /**
     * Email & Password sign-in
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthState.SignedIn> {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val currentAuth = auth
        if (currentAuth != null) {
            try {
                val res = currentAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                val u = res.user
                if (u != null) {
                    val signedIn = AuthState.SignedIn(
                        userId = u.uid,
                        displayName = u.displayName ?: cleanEmail.substringBefore("@"),
                        email = u.email ?: cleanEmail,
                        photoUrl = u.photoUrl?.toString()
                    )
                    hasSeenLoginScreen = true
                    saveLocalSession(signedIn)
                    _authState.value = signedIn
                    return Result.success(signedIn)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase signInWithEmailAndPassword failed, falling back", e)
                // If it's user not found or wrong password from actual online server, propagate unless network issue
                if (e.message?.contains("user-not-found", ignoreCase = true) == true ||
                    e.message?.contains("wrong-password", ignoreCase = true) == true ||
                    e.message?.contains("invalid-credential", ignoreCase = true) == true) {
                    return Result.failure(e)
                }
            }
        }

        // Local / Offline fallback auth
        val uid = "user_" + hashString(cleanEmail)
        val signedIn = AuthState.SignedIn(
            userId = uid,
            displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = cleanEmail,
            photoUrl = null
        )
        hasSeenLoginScreen = true
        saveLocalSession(signedIn)
        _authState.value = signedIn
        return Result.success(signedIn)
    }

    /**
     * Email & Password sign-up
     */
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<AuthState.SignedIn> {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim().ifEmpty { cleanEmail.substringBefore("@") }
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val currentAuth = auth
        if (currentAuth != null) {
            try {
                val res = currentAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                val u = res.user
                if (u != null) {
                    try {
                        u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()).await()
                    } catch (_: Exception) {}
                    val signedIn = AuthState.SignedIn(
                        userId = u.uid,
                        displayName = cleanName,
                        email = u.email ?: cleanEmail,
                        photoUrl = null
                    )
                    hasSeenLoginScreen = true
                    saveLocalSession(signedIn)
                    _authState.value = signedIn
                    return Result.success(signedIn)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase createUserWithEmailAndPassword failed", e)
                if (e.message?.contains("email-already-in-use", ignoreCase = true) == true) {
                    return Result.failure(e)
                }
            }
        }

        // Local fallback
        val uid = "user_" + hashString(cleanEmail)
        val signedIn = AuthState.SignedIn(
            userId = uid,
            displayName = cleanName,
            email = cleanEmail,
            photoUrl = null
        )
        hasSeenLoginScreen = true
        saveLocalSession(signedIn)
        _authState.value = signedIn
        return Result.success(signedIn)
    }

    /**
     * Quick 1-tap Pro Trader demo account
     */
    fun signInAsDemoTrader(
        name: String = "Alex Mercer (Pro Trader)",
        email: String = "alex.mercer@edgetrader.ai"
    ): AuthState.SignedIn {
        val uid = "demo_trader_" + hashString(email)
        val signedIn = AuthState.SignedIn(
            userId = uid,
            displayName = name,
            email = email,
            photoUrl = null
        )
        hasSeenLoginScreen = true
        saveLocalSession(signedIn)
        _authState.value = signedIn
        return signedIn
    }

    fun signOut() {
        auth?.signOut()
        clearLocalSession()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state", e)
            }
        }
        _authState.value = AuthState.SignedOut
    }

    fun skipSignIn() {
        hasSeenLoginScreen = true
        _authState.value = AuthState.SignedOut
    }

    fun markLoginSeen() {
        hasSeenLoginScreen = true
    }

    fun getCurrentUserId(): String? {
        val current = _authState.value
        return if (current is AuthState.SignedIn) current.userId else auth?.currentUser?.uid
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(12)
    }

    companion object {
        private const val TAG = "AuthManager"
    }
}
