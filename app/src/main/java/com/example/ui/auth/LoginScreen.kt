package com.example.ui.auth

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.auth.AuthManager
import com.example.auth.AuthState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onSignInSuccess: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = PrimaryBlueContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "EdgeTrader",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                "On-Device Algorithmic Trading Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val activity = context as? Activity
                        if (activity != null) {
                            val result = authManager.signInWithGoogle(activity)
                            isLoading = false
                            if (result.isSuccess) {
                                onSignInSuccess()
                            } else {
                                val ex = result.exceptionOrNull()
                                if (ex is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                    errorMessage = "Account not found. Please create an account first."
                                } else {
                                    errorMessage = "Sign-in was cancelled or failed. You can skip to use locally."
                                }
                            }
                        } else {
                            isLoading = false
                            errorMessage = "Unable to launch sign-in. You can skip to use locally."
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isLoading) "Signing in..." else "Sign in with Google",
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = CrimsonContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonDark,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onSkip,
                enabled = !isLoading
            ) {
                Text(
                    "Skip — use locally without cloud sync",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Sign in to sync your trading data across devices.\nYour data is stored securely in your personal Firestore database.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
