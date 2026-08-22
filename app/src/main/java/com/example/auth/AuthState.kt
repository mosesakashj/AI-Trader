package com.example.auth

sealed class AuthState {
    data object Loading : AuthState()
    data object SignedOut : AuthState()
    data class SignedIn(
        val userId: String,
        val displayName: String,
        val email: String,
        val photoUrl: String?
    ) : AuthState()
}
