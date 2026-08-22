package com.example.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun SecurityDocumentationScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("security_header_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = CyanLight)
                        Text("Architecture & Security Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "EdgeTrader is an on-device algorithmic trading system engineered with strict defense-in-depth safety controls and transparency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section 1: MT5 Bridge Real-World Constraints
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = GoldHero)
                        Text("1. MetaTrader 5 & Broker API Limitations", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text(
                        "• Native Android Constraint: MetaQuotes (MT4/MT5) does NOT expose a public native Android SDK/API for automated Expert Advisor (EA) trade placement.\n" +
                                "• Architecture: Live execution connects to a dedicated REST/WebSocket or FIX Gateway bridge. EdgeTrader contains a safety-stub LiveBrokerAdapter that explicitly blocks orders unless paired with a verified authenticated gateway.\n" +
                                "• Zero Blind Execution: No order is placed without strict pre-trade math validation and risk budget verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section 2: On-Device Determinism
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldGain)
                        Text("2. On-Device Determinism & Zero Backend", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text(
                        "• All technical indicator calculations (EMA, ADX, ATR), signal decision matrices, and risk sizing formulas run 100% locally on the device CPU.\n" +
                                "• There are no hidden third-party cloud servers intercepting your strategies or account numbers.\n" +
                                "• All trade history, active positions, and logs are persisted directly to SQLite via Room Database on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section 3: Hardware KeyStore Encryption
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CyanLight)
                        Text("3. Hardware-Backed Secret Storage", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text(
                        "• API tokens, Telegram Bot tokens, and passwords are encrypted using AES-256-GCM authenticated encryption.\n" +
                                "• Encryption keys are generated inside the Android KeyStore (TEE / Secure Enclave) and never exported in plaintext to memory or logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section 4: Watchdog & Safe Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4. Watchdog & Safe Mode Latch", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "• Autonomous watchdog detects stalled threads and heartbeat dropouts (>60s) and performs automated recovery.\n" +
                                "• If more than 3 recovery attempts fail or local positions do not match broker state, the system locks into SAFE MODE and requires manual operator acknowledgment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
