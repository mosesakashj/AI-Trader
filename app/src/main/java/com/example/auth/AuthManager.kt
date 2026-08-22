package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val webClientId: String by lazy {
        context.getString(
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        )
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _authState.value = AuthState.SignedIn(
                    userId = user.uid,
                    displayName = user.displayName ?: "Trader",
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString()
                )
            } else {
                if (_authState.value is AuthState.Loading) {
                    _authState.value = AuthState.SignedOut
                }
            }
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<AuthState.SignedIn> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val googleCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                val authResult = auth.signInWithCredential(googleCredential).await()
                val user = authResult.user!!

                val signedIn = AuthState.SignedIn(
                    userId = user.uid,
                    displayName = user.displayName ?: "Trader",
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString()
                )
                _authState.value = signedIn
                Result.success(signedIn)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(
            androidx.credentials.ClearCredentialStateRequest()
        )
        _authState.value = AuthState.SignedOut
    }

    fun skipSignIn() {
        _authState.value = AuthState.SignedOut
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    companion object {
        private const val TAG = "AuthManager"
    }
}
