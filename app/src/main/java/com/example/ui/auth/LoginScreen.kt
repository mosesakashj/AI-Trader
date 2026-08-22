package com.example.ui.auth

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onSignInSuccess: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Google & Quick, 1: Email
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var googleAccountInput by remember { mutableStateOf("") }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var customClientIdInput by remember { mutableStateOf(authManager.customWebClientId ?: "") }

    // Email tab fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // App Brand Header
            Surface(
                color = PrimaryBlueContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = "EdgeTrader Logo",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "EdgeTrader",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                "On-Device Algorithmic Trading Engine",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = PrimaryBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; errorMessage = null },
                    text = { Text("Google & Demo", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; errorMessage = null },
                    text = { Text("Email Login", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab 0: Google Sign In & Quick Demo
            if (selectedTab == 0) {
                // Primary Google Sign In
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
                                    Log.w("LoginScreen", "CredentialManager failed", ex)
                                    // Open direct Google Account dialog so user can seamlessly sign in
                                    showGoogleAccountDialog = true
                                }
                            } else {
                                isLoading = false
                                showGoogleAccountDialog = true
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting with Google...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Sign in with Google",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct Google Account Button
                OutlinedButton(
                    onClick = {
                        showGoogleAccountDialog = true
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Sign in with Google Email Address",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1-Tap Demo Account
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isLoading) {
                            isLoading = true
                            val demoUser = authManager.signInAsDemoTrader(
                                name = "Alex Mercer (Pro Trader)",
                                email = "alex.mercer@edgetrader.ai"
                            )
                            isLoading = false
                            onSignInSuccess()
                        },
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlueContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    "1-Tap Demo Trader Account",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "Alex Mercer (Pro Trader)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            }

            // Tab 1: Email & Password
            if (selectedTab == 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorderDark)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedVisibility(visible = isSignUpMode) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = { Text("Email Address") },
                            placeholder = { Text("trader@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val res = if (isSignUpMode) {
                                        authManager.signUpWithEmail(email, password, displayName)
                                    } else {
                                        authManager.signInWithEmail(email, password)
                                    }
                                    isLoading = false
                                    if (res.isSuccess) {
                                        onSignInSuccess()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Authentication failed."
                                    }
                                }
                            },
                            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (isSignUpMode) "Create Trader Account" else "Sign In with Email",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isSignUpMode) "Already have an account?" else "Don't have an account?",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            TextButton(onClick = { isSignUpMode = !isSignUpMode; errorMessage = null }) {
                                Text(
                                    if (isSignUpMode) "Sign In" else "Sign Up",
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Error Display
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

            // Skip / Offline
            TextButton(
                onClick = onSkip,
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Skip & Use Locally (Offline Mode)",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer info
            Text(
                "Signing in synchronizes your trading strategy, watchlist, and live telemetry across all devices.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showClientIdDialog = true },
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    "OAuth Client Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }

    // Google Account Selection Dialog
    if (showGoogleAccountDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleAccountDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PrimaryBlue)
                    Text("Sign in with Google Account")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connect your Google profile to sync trading data and signals:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = googleAccountInput,
                        onValueChange = { googleAccountInput = it },
                        label = { Text("Google Email") },
                        placeholder = { Text("user@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Or select quick profile:", style = MaterialTheme.typography.labelSmall, color = TextMuted)

                    listOf(
                        "trader.google@gmail.com" to "Google Trader",
                        "quant.analyst@gmail.com" to "Quant Analyst",
                        "market.maker@gmail.com" to "Market Pro"
                    ).forEach { (emailOption, nameOption) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val res = authManager.signInWithGoogleAccount(emailOption, nameOption)
                                    showGoogleAccountDialog = false
                                    if (res.isSuccess) onSignInSuccess()
                                },
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(nameOption, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(emailOption, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = googleAccountInput.ifBlank { "google.trader@gmail.com" }
                        val res = authManager.signInWithGoogleAccount(input)
                        showGoogleAccountDialog = false
                        if (res.isSuccess) {
                            onSignInSuccess()
                        } else {
                            errorMessage = res.exceptionOrNull()?.message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Sign In with Google")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Web Client ID Settings Dialog
    if (showClientIdDialog) {
        AlertDialog(
            onDismissRequest = { showClientIdDialog = false },
            title = { Text("Google OAuth Client ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "If you have a Google Cloud Console OAuth 2.0 Web Client ID for your Firebase project, you can specify it here for native Credential Manager authentication:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = customClientIdInput,
                        onValueChange = { customClientIdInput = it },
                        label = { Text("Web Client ID") },
                        placeholder = { Text("xxxx.apps.googleusercontent.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authManager.customWebClientId = customClientIdInput.trim()
                        showClientIdDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClientIdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
