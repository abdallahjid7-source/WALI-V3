package com.example.wali.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.wali.online.OnlineGameSession
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Boîte de dialogue proposant de reprendre une partie multijoueur en ligne en cours détectée sur Firestore.
 */
@Composable
fun ResumeOnlineGameDialog(
    session: OnlineGameSession,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        modifier = Modifier.widthIn(max = 440.dp),
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = colors.accentGold,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Partie en ligne en cours !",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Une partie multijoueur non terminée est active sur le serveur :",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
                )

                Text(
                    text = "Code : ${session.code}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentGold
                    )
                )

                Text(
                    text = "Souhaitez-vous vous reconnecter immédiatement ?",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGold,
                    contentColor = colors.surface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_resume_online_confirm")
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reprendre", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_resume_online_dismiss")
            ) {
                Text("Ignorer", color = colors.textPrimary)
            }
        }
    )
}
