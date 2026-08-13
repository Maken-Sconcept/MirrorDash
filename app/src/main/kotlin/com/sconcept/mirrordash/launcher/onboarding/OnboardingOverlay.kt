package com.sconcept.mirrordash.launcher.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * A single-screen welcome (brief section 41: "don't turn onboarding into eight screens").
 * Weather and NAS/Photorama are deliberately not part of this flow - both are optional and
 * fully configurable from Settings whenever the user gets to them, so forcing that decision
 * up front would only add friction to becoming the Home app, which is the one step that
 * actually needs a system dialog.
 */
@Composable
fun OnboardingOverlay(onSetAsHome: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MDTheme.colors.scrim)
            .padding(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome to MirrorDash", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Set MirrorDash as your Home app to use it as your everyday screen. You can always change this later in Settings.",
            style = MDTheme.type.body,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(420.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSetAsHome,
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Set as Home")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text("Not now", color = MDTheme.colors.textSecondary)
        }
    }
}
