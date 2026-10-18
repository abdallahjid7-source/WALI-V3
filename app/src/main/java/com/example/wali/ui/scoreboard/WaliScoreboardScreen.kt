package com.example.wali.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.Player
import com.example.wali.storage.GameOutcome
import com.example.wali.storage.GameResultRecord
import com.example.wali.storage.ScoreboardStats
import com.example.wali.ui.theme.LocalWaliColors
import java.util.Locale

/**
 * Écran complet et persistant du Tableau des scores (DataStore).
 * Affiche :
 * - Victoires, défaites, taux de succès et séries gagnantes
 * - Durée totale de jeu, durée moyenne par partie, victoire la plus rapide
 * - Décomposition détaillée par mode (vs Machine, En Ligne, 2 Joueurs)
 * - Historique chronologique des parties avec résultat, durée et date
 * - Option de réinitialisation sécurisée
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliScoreboardScreen(
    stats: ScoreboardStats,
    recentGames: List<GameResultRecord>,
    onResetScoreboard: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tableau des scores",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Statistiques & Durée de jeu",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .testTag("btn_scoreboard_back")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour au jeu",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    if (stats.totalGamesPlayed > 0) {
                        IconButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier
                                .testTag("btn_scoreboard_reset")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Réinitialiser les scores",
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // 1. CARTE PRINCIPALE : RÉSUMÉ DES VICTOIRES / DÉFAITES / SÉRIES
                    HeroScoreboardCard(stats = stats)
                }

                item {
                    // 2. CARTE : STATISTIQUES DE DURÉE DE JEU
                    DurationStatsCard(stats = stats)
                }

                item {
                    // 3. CARTE : DÉCOMPOSITION PAR MODE DE JEU
                    GameModeBreakdownCard(stats = stats)
                }

                item {
                    // 4. TITRE DE SECTION HISTORIQUE DES PARTIES
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historique des parties",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = "${recentGames.size} enregistrée(s)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.textSecondary
                            )
                        )
                    }
                }

                if (recentGames.isEmpty()) {
                    item {
                        EmptyHistoryCard()
                    }
                } else {
                    items(
                        items = recentGames,
                        key = { it.id }
                    ) { record ->
                        GameResultItemCard(record = record)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Boîte de dialogue de confirmation de réinitialisation
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            modifier = Modifier.testTag("reset_scoreboard_dialog"),
            title = {
                Text(
                    text = "Réinitialiser les scores ?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Cette action effacera définitivement l'ensemble des victoires, défaites, durées enregistrées et l'historique des parties.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetScoreboard()
                    },
                    modifier = Modifier.testTag("btn_confirm_reset_scoreboard")
                ) {
                    Text("Effacer tout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surface
        )
    }
}

/**
 * Carte principale affichant le bilan général : Victoires, Défaites, Taux de victoire et Séries.
 */
@Composable
private fun HeroScoreboardCard(
    stats: ScoreboardStats,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val winGreen = Color(0xFF2E7D32)
    val lossRed = Color(0xFFC62828)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scoreboard_hero_card")
            .border(1.5.dp, colors.accentGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.accentGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Bilan Général",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${stats.totalGamesPlayed} parties",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grille de statistiques : Victoires | Défaites | Taux
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Victoires
                StatCounterItem(
                    label = "Victoires",
                    value = stats.totalWins.toString(),
                    color = winGreen,
                    icon = Icons.Default.CheckCircle
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(colors.boardBorder.copy(alpha = 0.3f))
                )

                // Défaites
                StatCounterItem(
                    label = "Défaites",
                    value = stats.totalLosses.toString(),
                    color = lossRed,
                    icon = Icons.Default.RemoveCircle
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(colors.boardBorder.copy(alpha = 0.3f))
                )

                // Taux de victoire
                val winRateStr = String.format(Locale.US, "%.1f%%", stats.winRatePercent)
                StatCounterItem(
                    label = "Taux de succès",
                    value = winRateStr,
                    color = colors.accentGold,
                    icon = Icons.Default.Speed
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Jauge visuelle de taux de victoire
            val progressFraction = (stats.winRatePercent / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = winGreen,
                trackColor = lossRed.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Séries : Actuelle et Meilleure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreakBadge(
                    label = "Série actuelle",
                    count = stats.currentWinStreak,
                    icon = Icons.Default.Whatshot,
                    tint = colors.accentGold,
                    modifier = Modifier.weight(1f)
                )

                StreakBadge(
                    label = "Meilleure série",
                    count = stats.bestWinStreak,
                    icon = Icons.Default.EmojiEvents,
                    tint = colors.accentEarth,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Carte affichant les données de durée de jeu (total, moyenne, record le plus rapide, record le plus long).
 */
@Composable
private fun DurationStatsCard(
    stats: ScoreboardStats,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scoreboard_duration_card")
            .border(1.dp, colors.boardBorder.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.accentEarth.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = colors.accentEarth,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Durée & Temps de jeu",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DurationMetricItem(
                    label = "Temps total",
                    value = stats.formattedTotalDuration,
                    icon = Icons.Default.AccessTime,
                    modifier = Modifier.weight(1f)
                )
                DurationMetricItem(
                    label = "Moyenne / partie",
                    value = stats.formattedAverageDuration,
                    icon = Icons.Default.HourglassBottom,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DurationMetricItem(
                    label = "Victoire la plus rapide",
                    value = stats.formattedFastestWin,
                    icon = Icons.Default.Speed,
                    highlight = stats.fastestWinSeconds != null,
                    modifier = Modifier.weight(1f)
                )
                DurationMetricItem(
                    label = "Partie la plus longue",
                    value = stats.formattedLongestGame,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Carte de décomposition des résultats et durées par mode de jeu.
 */
@Composable
private fun GameModeBreakdownCard(
    stats: ScoreboardStats,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scoreboard_breakdown_card")
            .border(1.dp, colors.boardBorder.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Performance par Mode",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Contre la Machine
            ModeRowItem(
                icon = Icons.Default.SmartToy,
                title = "Contre la Machine",
                subtitle = "Facile (${stats.easyAiWins}V / ${stats.easyAiLosses}D) • Moyen (${stats.mediumAiWins}V / ${stats.mediumAiLosses}D) • Difficile (${stats.hardAiWins}V / ${stats.hardAiLosses}D)",
                wins = stats.vsComputerWins,
                losses = stats.vsComputerLosses,
                durationFormatted = com.example.wali.storage.formatDuration(stats.vsComputerDurationSeconds)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. En Ligne
            ModeRowItem(
                icon = Icons.Default.Public,
                title = "En Ligne (Multijoueur)",
                subtitle = "Matchs à distance via Internet",
                wins = stats.onlineWins,
                losses = stats.onlineLosses,
                durationFormatted = com.example.wali.storage.formatDuration(stats.onlineDurationSeconds)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 2 Joueurs Local
            ModeRowItem(
                icon = Icons.Default.Person,
                title = "2 Joueurs Local",
                subtitle = "Parties amicales sur le même appareil",
                wins = null,
                losses = null,
                customMetric = "${stats.twoPlayersGames} parties",
                durationFormatted = com.example.wali.storage.formatDuration(stats.twoPlayersDurationSeconds)
            )
        }
    }
}

/**
 * Ligne présentant les performances pour un mode donné.
 */
@Composable
private fun ModeRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    wins: Int?,
    losses: Int?,
    durationFormatted: String,
    customMetric: String? = null
) {
    val colors = LocalWaliColors.current
    val winGreen = Color(0xFF2E7D32)
    val lossRed = Color(0xFFC62828)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accentEarth,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }

                if (wins != null && losses != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${wins}V",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = winGreen
                            )
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.textSecondary
                            )
                        )
                        Text(
                            text = "${losses}D",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = lossRed
                            )
                        )
                    }
                } else if (customMetric != null) {
                    Text(
                        text = customMetric,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "⏱ $durationFormatted",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.accentGold,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * Élément compteur numérique simple (Victoires, Défaites, Taux).
 */
@Composable
private fun StatCounterItem(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector
) {
    val colors = LocalWaliColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * Badge pour les séries de victoires.
 */
@Composable
private fun StreakBadge(
    label: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "$count partie${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Bloc affichant une métrique temporelle.
 */
@Composable
private fun DurationMetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) colors.accentGold else colors.accentEarth,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) colors.accentGold else colors.textPrimary
                    )
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Carte représentant une partie enregistrée dans l'historique récent.
 */
@Composable
private fun GameResultItemCard(
    record: GameResultRecord,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val winGreen = Color(0xFF2E7D32)
    val lossRed = Color(0xFFC62828)

    val (badgeBg, badgeText, badgeLabel) = when (record.outcome) {
        GameOutcome.WIN -> Triple(winGreen.copy(alpha = 0.15f), winGreen, "Victoire")
        GameOutcome.LOSS -> Triple(lossRed.copy(alpha = 0.15f), lossRed, "Défaite")
        GameOutcome.COMPLETED -> Triple(colors.accentGold.copy(alpha = 0.2f), colors.accentGold, "Terminée")
    }

    val modeLabel = when (record.gameMode) {
        GameMode.VS_COMPUTER -> {
            val diff = when (record.aiDifficulty) {
                AiDifficulty.EASY -> "Facile"
                AiDifficulty.MEDIUM -> "Moyen"
                AiDifficulty.HARD, null -> "Difficile"
            }
            "vs IA ($diff)"
        }
        GameMode.ONLINE -> "En Ligne"
        GameMode.TWO_PLAYERS -> "2 Joueurs Local"
    }

    val winnerLabel = if (record.winner == Player.STICKS) "BÂTONNETS" else "CAILLOUX"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("game_result_item_${record.id}")
            .border(1.dp, colors.boardBorder.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        )
                    }

                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val reasonText = if (record.reason.isNotBlank()) " • ${record.reason}" else ""
                Text(
                    text = "Vainqueur : $winnerLabel$reasonText",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                )

                if (record.formattedDate.isNotBlank()) {
                    Text(
                        text = record.formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⏱ ${record.formattedDuration}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.accentGold
                    )
                )
            }
        }
    }
}

/**
 * Message d'état vide si aucune partie n'est encore enregistrée.
 */
@Composable
private fun EmptyHistoryCard() {
    val colors = LocalWaliColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "📊", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aucune partie enregistrée",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Terminez une partie contre la machine, en ligne ou à deux joueurs pour enregistrer vos victoires et durées de match.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
