// [NEW FILE] The Settings screen: theme picker, "Email code at login" switch, Log out.
package com.moneymate.app.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accountEmail: String,
    emailCodeAtLogin: Boolean,
    onEmailCodeAtLoginChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: (() -> Unit)?
) {
    BackHandler(onBack = onBack)
    val colors = MoneyMateTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
            Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        }

        SectionLabel("APPEARANCE")
        SettingsCard {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = themeMode == mode, onClick = { onThemeModeChange(mode) }, role = Role.RadioButton)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(mode.label, color = colors.ink)
                    }
                }
            }
        }

        SectionLabel("SECURITY")
        SettingsCard {
            val hasEmail = accountEmail.isNotBlank()
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = emailCodeAtLogin && hasEmail, enabled = hasEmail, role = Role.Switch, onValueChange = onEmailCodeAtLoginChange)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Email code at login", color = colors.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasEmail) "After your password, enter a code sent to $accountEmail"
                        else "This account has no email. Create a new account with an email to turn this on.",
                        color = colors.muted, fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = emailCodeAtLogin && hasEmail, onCheckedChange = null, enabled = hasEmail)
            }
        }

        if (onLogout != null) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Log out")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MoneyMateTheme.colors.muted, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MoneyMateTheme.colors.surface)) { content() }
}
