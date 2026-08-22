package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun EmergencyStopDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Dangerous, contentDescription = null, tint = CrimsonLoss, modifier = Modifier.size(36.dp)) },
        title = { Text("TRIGGER EMERGENCY STOP?", fontWeight = FontWeight.Bold, color = CrimsonLoss) },
        text = {
            Text(
                "This will immediately HALT all new trade entries, persist the emergency state locally across reboots, " +
                        "and notify your Telegram channel. Existing positions will continue to be monitored.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                modifier = Modifier.testTag("confirm_emergency_stop_btn")
            ) {
                Text("HALT TRADING NOW", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun CloseAllPositionsDialog(
    positionCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmationInput by remember { mutableStateOf("") }
    val isValidConfirmation = confirmationInput.trim() == "CLOSE ALL"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(36.dp)) },
        title = { Text("CLOSE ALL POSITIONS", fontWeight = FontWeight.Bold, color = StatusWarning) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You are about to market-liquidate all $positionCount active positions immediately at the current market bid/ask.",
                    color = TextSecondary
                )
                Text(
                    "To confirm, type CLOSE ALL in capital letters below:",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = confirmationInput,
                    onValueChange = { confirmationInput = it },
                    placeholder = { Text("CLOSE ALL", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonLoss,
                        unfocusedBorderColor = CardBorderDark
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("close_all_confirmation_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isValidConfirmation,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                modifier = Modifier.testTag("execute_close_all_btn")
            ) {
                Text("LIQUIDATE ALL", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun LiveModeDisclaimerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmationInput by remember { mutableStateOf("") }
    val isValidConfirmation = confirmationInput.trim() == "I UNDERSTAND THE RISKS"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Dangerous, contentDescription = null, tint = CrimsonLoss, modifier = Modifier.size(36.dp)) },
        title = { Text("LIVE TRADING DISCLAIMER", fontWeight = FontWeight.Bold, color = CrimsonLoss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Trading CFDs, Forex, and Crypto carries a high level of risk of capital loss. Past backtested performance is NEVER a guarantee of future results.",
                    color = TextSecondary
                )
                Text(
                    "On-device mobile trading is subject to OS battery optimization, background sleep, and mobile data dropouts.",
                    color = TextSecondary
                )
                Text(
                    "Type 'I UNDERSTAND THE RISKS' to enable Live Mode:",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = confirmationInput,
                    onValueChange = { confirmationInput = it },
                    placeholder = { Text("I UNDERSTAND THE RISKS", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonLoss,
                        unfocusedBorderColor = CardBorderDark
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("live_mode_disclaimer_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isValidConfirmation,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                modifier = Modifier.testTag("confirm_live_mode_btn")
            ) {
                Text("ENABLE LIVE MODE", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}
