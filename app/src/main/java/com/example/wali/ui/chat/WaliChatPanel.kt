package com.example.wali.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wali.chat.ChatMessageType
import com.example.wali.chat.WaliChatMessage
import com.example.wali.chat.WaliChatUiState
import com.example.wali.model.Player
import com.example.wali.ui.theme.LocalWaliColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bouton icône avec badge pour ouvrir ou fermer le chat multijoueur.
 */
@Composable
fun WaliChatButton(
    unreadCount: Int,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    IconButton(
        onClick = onClick,
        modifier = modifier
            .testTag("btn_chat_toggle")
            .size(48.dp)
            .semantics { contentDescription = "Ouvrir ou fermer le chat de la partie" }
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0 && !isOpen) {
                    Badge(
                        containerColor = colors.accentEarth,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("chat_unread_badge")
                    ) {
                        Text(
                            text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = null,
                tint = if (isOpen) colors.accentGold else colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Panneau repliable du chat en cours de partie multijoueur.
 *
 * Conçu pour ne JAMAIS recouvrir entièrement le plateau afin de permettre de continuer à jouer.
 */
@Composable
fun WaliChatPanel(
    chatState: WaliChatUiState,
    localPlayerRole: Player?,
    isLandscape: Boolean,
    onClose: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendQuickMessage: (String) -> Unit,
    onInputChanged: (String) -> Unit,
    onBlockOpponent: () -> Unit,
    onOpenReport: () -> Unit,
    onDismissWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val listState = rememberLazyListState()

    // Auto-scroll vers le dernier message
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    val panelHeight = if (isLandscape) 280.dp else 290.dp
    val panelWidthModifier = if (isLandscape) Modifier.widthIn(max = 380.dp) else Modifier.fillMaxWidth()

    Card(
        modifier = modifier
            .then(panelWidthModifier)
            .height(panelHeight)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("chat_panel")
            .border(1.5.dp, colors.accentGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // --- EN-TÊTE DU CHAT ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null,
                        tint = colors.accentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chat de la partie",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bouton Bloquer / Masquer l'adversaire
                    if (!chatState.isOpponentBlocked) {
                        IconButton(
                            onClick = onBlockOpponent,
                            modifier = Modifier
                                .testTag("btn_block_opponent")
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Masquer le chat de l'adversaire",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Bouton Signaler
                    IconButton(
                        onClick = onOpenReport,
                        modifier = Modifier
                            .testTag("btn_report_opponent")
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Signaler l'adversaire",
                            tint = colors.accentEarth,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Bouton Fermer le panneau
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("btn_close_chat")
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer le chat",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // --- BANNIÈRE D'AVERTISSEMENT INITIAL (MODÉRATION) ---
            if (chatState.showWarningBanner) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("chat_warning_banner"),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceAlt
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Restez respectueux. Les messages abusifs peuvent être signalés.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismissWarning,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("OK", fontSize = 10.sp, color = colors.accentGold)
                        }
                    }
                }
            }

            // --- AVIS D'ERREUR OU DE DÉBIT ---
            if (chatState.rateLimitNotice != null) {
                Text(
                    text = chatState.rateLimitNotice,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("chat_rate_limit_notice")
                )
            }

            if (!chatState.isConnected) {
                Text(
                    text = "Chat indisponible (hors ligne)",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // --- LISTE DES MESSAGES ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (chatState.messages.isEmpty()) {
                    item {
                        Text(
                            text = "Aucun message pour l'instant. Dites bonjour à votre adversaire !",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colors.textSecondary,
                                fontStyle = FontStyle.Italic
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }

                items(chatState.messages, key = { it.id.ifBlank { it.timestamp.toString() } }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        isMine = msg.senderRole == localPlayerRole,
                        localPlayerRole = localPlayerRole
                    )
                }
            }

            // --- MESSAGES RAPIDES EN UN TAP ---
            if (!chatState.isOpponentBlocked) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("chat_quick_messages_row"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(WaliChatMessage.QUICK_MESSAGES) { quickText ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceAlt,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentGold.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSendQuickMessage(quickText) }
                                .testTag("btn_quick_${quickText.take(6).trim()}")
                        ) {
                            Text(
                                text = quickText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.textPrimary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // --- BARRE DE SAISIE AVEC COMPTEUR DE CARACTÈRES ---
            if (!chatState.isOpponentBlocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatState.inputText,
                        onValueChange = onInputChanged,
                        placeholder = {
                            Text(
                                "Écrire un message…",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        },
                        singleLine = true,
                        maxLines = 1,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textPrimary,
                            fontSize = 12.sp
                        ),
                        trailingIcon = {
                            Text(
                                text = "${chatState.inputText.length}/200",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = if (chatState.inputText.length >= 190) MaterialTheme.colorScheme.error else colors.textSecondary
                                ),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentGold,
                            unfocusedBorderColor = colors.gridLine.copy(alpha = 0.4f),
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val canSend = chatState.inputText.trim().isNotEmpty()
                    IconButton(
                        onClick = { onSendMessage(chatState.inputText) },
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colors.accentGold else colors.surfaceAlt)
                            .testTag("btn_send_chat")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer le message",
                            tint = if (canSend) colors.surface else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "L'adversaire est masqué pour cette partie.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.textSecondary,
                        fontStyle = FontStyle.Italic
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Bulle individuelle de message.
 */
@Composable
private fun ChatBubbleItem(
    message: WaliChatMessage,
    isMine: Boolean,
    localPlayerRole: Player?
) {
    val colors = LocalWaliColors.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    if (message.type == ChatMessageType.SYSTEM) {
        // Message système neutre
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.textSecondary,
                    fontStyle = FontStyle.Italic,
                    fontSize = 10.sp
                ),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val bubbleColor = if (isMine) colors.accentGold.copy(alpha = 0.25f) else colors.surfaceAlt
    val borderColor = if (isMine) colors.accentGold.copy(alpha = 0.6f) else colors.gridLine.copy(alpha = 0.3f)
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart

    val roleLabel = when (message.senderRole) {
        Player.STICKS -> "Bâtonnets"
        Player.STONES -> "Cailloux"
        null -> ""
    }
    val authorLabel = if (isMine) "Vous ($roleLabel)" else "Adversaire ($roleLabel)"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(
                text = authorLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isMine) 12.dp else 2.dp,
                    bottomEnd = if (isMine) 2.dp else 12.dp
                ),
                color = bubbleColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textPrimary,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textSecondary,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Dialogue de signalement d'un adversaire.
 */
@Composable
fun WaliReportDialog(
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String) -> Unit
) {
    val colors = LocalWaliColors.current
    var selectedReason by remember { mutableStateOf("Propos injurieux ou déplacés") }
    val reasons = listOf(
        "Propos injurieux ou déplacés",
        "Spam ou comportement perturbateur",
        "Anti-jeu ou harcèlement",
        "Autre comportement inapproprié"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("report_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = colors.accentEarth,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Signaler l'adversaire",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }

                Text(
                    text = "Choisissez la raison du signalement. Les derniers messages échangés seront transmis pour examen.",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.accentEarth.copy(alpha = 0.2f) else colors.surfaceAlt,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) colors.accentEarth else colors.gridLine.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) colors.accentEarth else colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmitReport(selectedReason) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentEarth),
                        modifier = Modifier.testTag("btn_confirm_report")
                    ) {
                        Text("Envoyer le signalement", color = Color.White)
                    }
                }
            }
        }
    }
}
