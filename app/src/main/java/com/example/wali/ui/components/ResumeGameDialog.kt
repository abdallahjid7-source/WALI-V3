package com.example.wali.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Boîte de dialogue affichée au lancement lorsqu'une partie en cours non terminée a été détectée.
 *
 * Propose deux options :
 * - [onResume] : Recharger immédiatement la partie sauvegardée.
 * - [onDeclineNewGame] : Effacer la sauvegarde et ouvrir la sélection de mode pour une nouvelle partie.
 */
@Composable
fun ResumeGameDialog(
    onResume: () -> Unit,
    onDeclineNewGame: () -> Unit
) {
    val colors = LocalWaliColors.current

    AlertDialog(
        onDismissRequest = { /* Bloquant : l'utilisateur doit choisir entre reprendre ou recommencer */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        modifier = Modifier.widthIn(max = 440.dp),
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Une partie est en cours. Reprendre ?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Une partie interrompue a été retrouvée sur votre appareil. Souhaitez-vous la reprendre exactement là où vous vous étiez arrêté ?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onResume,
                modifier = Modifier.testTag("btn_resume_game"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentEarth,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reprendre", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeclineNewGame,
                modifier = Modifier.testTag("btn_decline_resume"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nouvelle partie",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
