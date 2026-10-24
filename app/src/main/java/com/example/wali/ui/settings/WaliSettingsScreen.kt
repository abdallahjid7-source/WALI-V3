package com.example.wali.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wali.ads.WaliAdManager
import com.example.wali.ai.WaliAiBenchmark
import com.example.wali.ai.WaliAiDiagnostics
import com.example.wali.ui.theme.LocalWaliColors
import com.example.wali.ui.theme.WaliThemePalette
import com.example.wali.ui.theme.getColorsForPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Écran des Réglages :
 * Écran des Réglages :
 * 1. Volumes audio (Effets sonores 0-100%, Musique de fond 0-100%, Interrupteur Mute global)
 * 2. Minuteur par coup (Activation, choix de la durée : 15s, 30s, 60s, 90s)
 * 3. Choix du thème visuel parmi les 3 thèmes officiels avec aperçu miniature du plateau
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliSettingsScreen(
    sfxVolume: Float,
    musicVolume: Float,
    isMuted: Boolean,
    selectedPalette: WaliThemePalette,
    isTimerEnabled: Boolean = true,
    timerDurationSeconds: Int = 30,
    isMandatoryAlignmentEnabled: Boolean = false,
    isDefeatAtTwoPiecesEnabled: Boolean = false,
    isCellNumbersEnabled: Boolean = true,
    onSfxVolumeChanged: (Float) -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onMutedChanged: (Boolean) -> Unit,
    onThemeSelected: (WaliThemePalette) -> Unit,
    onTimerEnabledChanged: (Boolean) -> Unit = {},
    onTimerDurationChanged: (Int) -> Unit = {},
    onMandatoryAlignmentChanged: (Boolean) -> Unit = {},
    onDefeatAtTwoPiecesChanged: (Boolean) -> Unit = {},
    onCellNumbersEnabledChanged: (Boolean) -> Unit = {},
    onOpenScoreboard: (() -> Unit)? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val context = LocalContext.current
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val secretTapCount = remember { mutableIntStateOf(0) }
    val showDiagnosticCard = isDebugBuild || secretTapCount.intValue >= 5

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .testTag("settings_header_title")
                            .clickable { secretTapCount.intValue++ }
                    ) {
                        Text(
                            text = "Réglages",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = "Sons, Minuteur et Thème visuel",
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
                            .testTag("btn_settings_back")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    if (onOpenScoreboard != null) {
                        IconButton(
                            onClick = onOpenScoreboard,
                            modifier = Modifier
                                .testTag("btn_settings_scoreboard")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Leaderboard,
                                contentDescription = "Tableau des scores",
                                tint = colors.accentGold
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
                    .padding(horizontal = 16.dp)
                    .testTag("settings_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Section 1 : Contrôle du son
                    AudioSettingsCard(
                        sfxVolume = sfxVolume,
                        musicVolume = musicVolume,
                        isMuted = isMuted,
                        onSfxVolumeChanged = onSfxVolumeChanged,
                        onMusicVolumeChanged = onMusicVolumeChanged,
                        onMutedChanged = onMutedChanged
                    )
                }

                item {
                    // Section 2 : Minuteur par coup
                    TimerSettingsCard(
                        isTimerEnabled = isTimerEnabled,
                        timerDurationSeconds = timerDurationSeconds,
                        onTimerEnabledChanged = onTimerEnabledChanged,
                        onTimerDurationChanged = onTimerDurationChanged
                    )
                }

                item {
                    // Section 3 : Variantes de règles
                    RuleVariantsSettingsCard(
                        isMandatoryAlignmentEnabled = isMandatoryAlignmentEnabled,
                        isDefeatAtTwoPiecesEnabled = isDefeatAtTwoPiecesEnabled,
                        onMandatoryAlignmentChanged = onMandatoryAlignmentChanged,
                        onDefeatAtTwoPiecesChanged = onDefeatAtTwoPiecesChanged
                    )
                }

                item {
                    // Section 4 : Thèmes visuels & affichage du plateau
                    ThemeSelectionCard(
                        currentPalette = selectedPalette,
                        onThemeSelected = onThemeSelected,
                        isCellNumbersEnabled = isCellNumbersEnabled,
                        onCellNumbersEnabledChanged = onCellNumbersEnabledChanged
                    )
                }

                item {
                    // Section 4 : Annonce récompensée / Soutien
                    RewardSupportCard()
                }

                if (showDiagnosticCard) {
                    item {
                        // Section 5 (Mode Debug ou déverrouillé par 5 pressions sur "Réglages") : Banc de mesure IA
                        AiDiagnosticBenchmarkCard()
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RewardSupportCard() {
    val colors = LocalWaliColors.current
    val context = LocalContext.current
    val rewardMessage = remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_reward_ad"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accentGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = "Récompense",
                        tint = colors.accentGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Soutenir le jeu",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Visionnez une courte vidéo récompensée pour soutenir Wali Songhaï",
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                    )
                }
            }

            Button(
                onClick = {
                    val activity = context as? Activity
                    if (activity != null) {
                        WaliAdManager.showRewardedAd(
                            activity = activity,
                            onUserEarnedReward = {
                                rewardMessage.value = "Merci pour votre précieux soutien à Wali Songhaï !"
                            },
                            onFinished = {}
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_watch_reward_ad"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.captureAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Regarder une annonce récompensée",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            rewardMessage.value?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.accentGold,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun AudioSettingsCard(
    sfxVolume: Float,
    musicVolume: Float,
    isMuted: Boolean,
    onSfxVolumeChanged: (Float) -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onMutedChanged: (Boolean) -> Unit
) {
    val colors = LocalWaliColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_audio_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accentGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Ambiance Sonore",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch Mute Global
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Couper tout le son (Mute)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Désactive la musique et les effets sans toucher aux curseurs",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = isMuted,
                    onCheckedChange = onMutedChanged,
                    modifier = Modifier.testTag("switch_mute_all"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accentEarth,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Curseur Effets sonores
            val sfxPercent = (sfxVolume * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.accentEarth,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Effets sonores (bâtonnets, cailloux, captures)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }
                Text(
                    text = "$sfxPercent%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.accentEarth
                    )
                )
            }

            Slider(
                value = sfxVolume,
                onValueChange = onSfxVolumeChanged,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_sfx_volume"),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentGold,
                    activeTrackColor = colors.accentGold,
                    inactiveTrackColor = colors.surfaceAlt
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Curseur Musique de fond
            val musicPercent = (musicVolume * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = colors.accentEarth,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Musique d'ambiance (nappe méditative)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }
                Text(
                    text = "$musicPercent%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.accentEarth
                    )
                )
            }

            Slider(
                value = musicVolume,
                onValueChange = onMusicVolumeChanged,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_music_volume"),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentGold,
                    activeTrackColor = colors.accentGold,
                    inactiveTrackColor = colors.surfaceAlt
                )
            )
        }
    }
}

@Composable
private fun ThemeSelectionCard(
    currentPalette: WaliThemePalette,
    onThemeSelected: (WaliThemePalette) -> Unit,
    isCellNumbersEnabled: Boolean = true,
    onCellNumbersEnabledChanged: (Boolean) -> Unit = {}
) {
    val colors = LocalWaliColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_theme_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accentGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Thème Visuel",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option d'affichage des numéros de cases (1 à 30)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .clickable { onCellNumbersEnabledChanged(!isCellNumbersEnabled) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Numéroter les cases (1 à 30)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Affiche un numéro discret dans chaque case pour faciliter les échanges dans le chat",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = isCellNumbersEnabled,
                    onCheckedChange = onCellNumbersEnabledChanged,
                    modifier = Modifier.testTag("switch_cell_numbers_enabled"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accentEarth,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WaliThemePalette.entries.forEach { palette ->
                ThemeOptionItem(
                    palette = palette,
                    isSelected = palette == currentPalette,
                    onSelect = { onThemeSelected(palette) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    palette: WaliThemePalette,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val currentColors = LocalWaliColors.current
    val paletteColors = getColorsForPalette(palette)

    val borderColor = if (isSelected) currentColors.accentGold else paletteColors.gridLine.copy(alpha = 0.3f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(paletteColors.background)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp)
            .testTag("theme_option_${palette.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aperçu miniature du plateau 3x3 dans la palette du thème
        MiniBoardPreview(paletteColors = paletteColors)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = palette.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = paletteColors.textPrimary
                    )
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(paletteColors.accentGold, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = paletteColors.textPrimary
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = palette.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = paletteColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
    }
}

/**
 * Aperçu miniature du plateau avec un bâtonnet et un caillou pour visualiser les contrastes.
 */
@Composable
private fun MiniBoardPreview(
    paletteColors: com.example.wali.ui.theme.WaliColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(paletteColors.cellBackground)
            .border(1.5.dp, paletteColors.boardBorder, RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Mini grille 2x2
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Mini case avec bâtonnet
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(paletteColors.cellBackgroundAlt, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(paletteColors.stickGradientMiddle)
                    )
                }
                // Mini case avec caillou
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(paletteColors.cellBackground, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(paletteColors.stoneGradientMid)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerSettingsCard(
    isTimerEnabled: Boolean,
    timerDurationSeconds: Int,
    onTimerEnabledChanged: (Boolean) -> Unit,
    onTimerDurationChanged: (Int) -> Unit
) {
    val colors = LocalWaliColors.current
    val durations = listOf(15, 30, 60, 90)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_timer_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accentGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Minuteur par Coup",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch Activer le minuteur
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Activer le minuteur",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Joue un coup aléatoire automatique si le temps expire",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = isTimerEnabled,
                    onCheckedChange = onTimerEnabledChanged,
                    modifier = Modifier.testTag("switch_timer_enabled"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accentEarth,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sélecteur de durée (15s, 30s, 60s, 90s)
            Text(
                text = "Durée par tour :",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTimerEnabled) colors.textPrimary else colors.textSecondary.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durations.forEach { seconds ->
                    val isSelected = seconds == timerDurationSeconds
                    val buttonAlpha = if (isTimerEnabled) 1f else 0.4f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected && isTimerEnabled) colors.accentGold
                                else colors.surfaceAlt.copy(alpha = buttonAlpha)
                            )
                            .border(
                                width = if (isSelected && isTimerEnabled) 2.dp else 1.dp,
                                color = if (isSelected && isTimerEnabled) colors.accentGold
                                else colors.gridLine.copy(alpha = 0.3f * buttonAlpha),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = isTimerEnabled) {
                                onTimerDurationChanged(seconds)
                            }
                            .padding(vertical = 10.dp)
                            .testTag("btn_timer_duration_$seconds"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${seconds}s",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected && isTimerEnabled) colors.textPrimary
                                else colors.textPrimary.copy(alpha = buttonAlpha)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleVariantsSettingsCard(
    isMandatoryAlignmentEnabled: Boolean,
    isDefeatAtTwoPiecesEnabled: Boolean,
    onMandatoryAlignmentChanged: (Boolean) -> Unit,
    onDefeatAtTwoPiecesChanged: (Boolean) -> Unit
) {
    val colors = LocalWaliColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_rule_variants_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accentGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Variantes de règles",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 1 : Alignement et capture obligatoires
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .clickable { onMandatoryAlignmentChanged(!isMandatoryAlignmentEnabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Alignement et capture obligatoires",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Si un déplacement permet de former un alignement de trois, le joueur doit le jouer.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Checkbox(
                    checked = isMandatoryAlignmentEnabled,
                    onCheckedChange = onMandatoryAlignmentChanged,
                    modifier = Modifier.testTag("checkbox_mandatory_alignment"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accentEarth,
                        uncheckedColor = colors.textSecondary,
                        checkmarkColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2 : Défaite à deux pions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .clickable { onDefeatAtTwoPiecesChanged(!isDefeatAtTwoPiecesEnabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Défaite à deux pions",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "En phase de déplacement, un joueur qui n'a plus que deux pions perd la partie.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Checkbox(
                    checked = isDefeatAtTwoPiecesEnabled,
                    onCheckedChange = onDefeatAtTwoPiecesChanged,
                    modifier = Modifier.testTag("checkbox_defeat_at_two_pieces"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accentEarth,
                        uncheckedColor = colors.textSecondary,
                        checkmarkColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Les variantes s'appliqueront à la prochaine partie.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("rule_variants_next_game_notice")
            )
        }
    }
}

@Composable
private fun AiDiagnosticBenchmarkCard() {
    val colors = LocalWaliColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRunning = remember { mutableStateOf(false) }
    val progressLabel = remember { mutableStateOf<String?>(null) }
    val reportText = remember { mutableStateOf<String?>(null) }
    val copiedFeedback = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_ai_diagnostic_benchmark"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Diagnostic IA",
                    tint = colors.accentGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Diagnostic IA — Banc de mesure (Appareil réel)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Exécute les 5 positions de référence (budget 1 000 ms chacune, ≈ 6 secondes au total) sur le processeur de cet appareil et affiche les métriques complètes (nœuds, nps, profondeur, répartition).",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!isRunning.value) {
                            isRunning.value = true
                            copiedFeedback.value = false
                            reportText.value = null
                            scope.launch {
                                val deviceHeader = "Appareil: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, SoC=${Build.HARDWARE})"
                                val suiteOutput = withContext(Dispatchers.Default) {
                                    WaliAiBenchmark.runFullDeviceSuite(
                                        budgetMs = 1000L,
                                        onProgress = { step ->
                                            progressLabel.value = step
                                        }
                                    )
                                }
                                val fullReport = "$deviceHeader\n$suiteOutput"
                                Log.i("WaliAiBenchmark", "\n$fullReport")
                                reportText.value = fullReport
                                progressLabel.value = null
                                isRunning.value = false
                            }
                        }
                    },
                    enabled = !isRunning.value,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_run_ai_benchmark"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentEarth,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isRunning.value) (progressLabel.value ?: "Mesure en cours...") else "Lancer les 5 bancs (6 s)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (reportText.value != null) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("WaliAiBenchmark", reportText.value))
                            copiedFeedback.value = true
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_copy_ai_benchmark"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (copiedFeedback.value) "Copié !" else "Copier",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                    }
                }
            }

            reportText.value?.let { report ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.7f))
                        .padding(10.dp)
                        .testTag("text_ai_benchmark_report")
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ligne de séparation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.gridLine.copy(alpha = 0.25f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Historique des coups de l'IA en partie réelle
            val realGameHistory = remember { mutableStateOf(WaliAiDiagnostics.getHistory()) }
            val historyCopied = remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Historique réel IA (${realGameHistory.value.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            realGameHistory.value = WaliAiDiagnostics.getHistory()
                            historyCopied.value = false
                        },
                        modifier = Modifier.testTag("btn_refresh_ai_history")
                    ) {
                        Text(
                            text = "Actualiser",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accentEarth
                            )
                        )
                    }

                    if (realGameHistory.value.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val textToCopy = realGameHistory.value.joinToString("\n\n") { it.formatDisplay() }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("WaliAiHistory", textToCopy))
                                historyCopied.value = true
                            },
                            modifier = Modifier.testTag("btn_copy_ai_history")
                        ) {
                            Text(
                                text = if (historyCopied.value) "Copié !" else "Copier",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (realGameHistory.value.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.5f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun coup d'IA enregistré. Jouez contre l'ordinateur en partie réelle pour afficher les métriques en direct (profondeur atteinte, nps, temps de recherche, raccourcis).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    realGameHistory.value.take(15).forEach { turn ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceAlt.copy(alpha = 0.6f))
                                .border(1.dp, colors.gridLine.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = turn.formatDisplay(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = colors.textPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


