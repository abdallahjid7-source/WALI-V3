package com.example.wali.ui.online

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wali.model.Player
import com.example.wali.online.PublicGameItem
import com.example.wali.online.WaliOnlineCodeGenerator
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Écran "Jouer en ligne" permettant de créer ou rejoindre une partie multijoueur à distance via Firestore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliOnlineScreen(
    waitingForOpponentCode: String?,
    publicWaitingGames: List<PublicGameItem>,
    isLoading: Boolean,
    errorMessage: String?,
    mandatoryAlignment: Boolean = false,
    defeatAtTwoPieces: Boolean = false,
    onCreateGame: (isPublic: Boolean, chosenSide: Player) -> Unit,
    onJoinGame: (code: String) -> Unit,
    onCancelWaiting: () -> Unit,
    onDismissError: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(if (waitingForOpponentCode != null) 0 else 0) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Partie en Ligne",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = "2 Joueurs à distance via Internet",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colors.textSecondary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (waitingForOpponentCode != null) {
                                onCancelWaiting()
                            }
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("btn_online_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface
                ),
                modifier = Modifier.testTag("online_top_app_bar")
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
            ) {
            // Bandeau d'erreur éventuelle
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("online_error_banner"),
                    colors = CardDefaults.cardColors(containerColor = colors.accentEarth.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fermer l'erreur",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Onglets de navigation : Créer une partie / Rejoindre une partie
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.surface,
                contentColor = colors.accentGold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = colors.accentGold
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Créer une partie",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) colors.accentGold else colors.textSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_create_game")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Rejoindre une partie",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) colors.accentGold else colors.textSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_join_game")
                )
            }

            // Contenu de l'onglet actif
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    CreateGameTabContent(
                        waitingCode = waitingForOpponentCode,
                        isLoading = isLoading,
                        mandatoryAlignment = mandatoryAlignment,
                        defeatAtTwoPieces = defeatAtTwoPieces,
                        onCreateGame = onCreateGame,
                        onCancelWaiting = onCancelWaiting,
                        context = context
                    )
                } else {
                    JoinGameTabContent(
                        publicGames = publicWaitingGames,
                        isLoading = isLoading,
                        onJoinGame = onJoinGame
                    )
                }

                // Indicateur de chargement global
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = colors.accentGold)
                                Text(
                                    text = "Connexion au salon…",
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Contenu de l'onglet "Créer une partie" : configuration ou attente d'adversaire.
 */
@Composable
private fun CreateGameTabContent(
    waitingCode: String?,
    isLoading: Boolean,
    mandatoryAlignment: Boolean = false,
    defeatAtTwoPieces: Boolean = false,
    onCreateGame: (isPublic: Boolean, chosenSide: Player) -> Unit,
    onCancelWaiting: () -> Unit,
    context: Context
) {
    val colors = LocalWaliColors.current

    if (waitingCode != null) {
        // Mode ATTENTE D'UN ADVERSAIRE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, colors.accentGold, RoundedCornerShape(20.dp))
                .testTag("card_waiting_for_opponent"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.accentGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.HourglassEmpty,
                        contentDescription = null,
                        tint = colors.accentGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "En attente d'un adversaire…",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Partagez ce code avec votre adversaire pour qu'il rejoigne la partie :",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )

                // Affichage grand format du code de partie
                Box(
                    modifier = Modifier
                        .background(colors.background, RoundedCornerShape(12.dp))
                        .border(1.5.dp, colors.accentGold, RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag("waiting_code_display")
                ) {
                    Text(
                        text = formatCodeSpaced(waitingCode),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            color = colors.accentGold
                        )
                    )
                }

                // Étiquettes des variantes actives du salon
                RuleVariantBadgesRow(
                    mandatoryAlignment = mandatoryAlignment,
                    defeatAtTwoPieces = defeatAtTwoPieces
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton Copier
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code Wali", waitingCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copié dans le presse-papiers !", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_copy_code")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier", color = colors.textPrimary)
                    }

                    // Bouton Partager (Intent standard)
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Rejoins ma partie de Wali Songhaï de Tombouctou avec le code : $waitingCode !"
                                )
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Partager le code de partie")
                            context.startActivity(shareIntent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentEarth,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_share_code")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager")
                    }
                }

                OutlinedButton(
                    onClick = onCancelWaiting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_cancel_waiting")
                ) {
                    Text("Annuler le salon", color = colors.accentEarth)
                }
            }
        }
    } else {
        // Mode CRÉATION DE PARTIE
        var chosenSide by remember { mutableStateOf(Player.STICKS) }
        var isPublic by remember { mutableStateOf(true) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Paramètres de la partie",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )

                        // 1. Choix du camp
                        Text(
                            text = "Votre camp :",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SelectableSideChip(
                                title = "BÂTONNETS",
                                subtitle = "Joue en 1er",
                                isSelected = chosenSide == Player.STICKS,
                                onClick = { chosenSide = Player.STICKS },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_side_sticks")
                            )
                            SelectableSideChip(
                                title = "CAILLOUX",
                                subtitle = "Joue en 2nd",
                                isSelected = chosenSide == Player.STONES,
                                onClick = { chosenSide = Player.STONES },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_side_stones")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Visibilité de la partie (Publique / Privée)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (isPublic) colors.accentGold else colors.textSecondary
                                )
                                Column {
                                    Text(
                                        text = if (isPublic) "Partie Publique" else "Partie Privée",
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (isPublic)
                                            "Visible par tous les joueurs dans la liste publique"
                                        else
                                            "Accessible uniquement aux joueurs ayant votre code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = isPublic,
                                onCheckedChange = { isPublic = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accentGold
                                ),
                                modifier = Modifier.testTag("switch_public_game")
                            )
                        }

                        // Variantes de règles actives pour ce salon (issues des réglages du créateur)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Règles du salon :",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            )
                            RuleVariantBadgesRow(
                                mandatoryAlignment = mandatoryAlignment,
                                defeatAtTwoPieces = defeatAtTwoPieces
                            )
                        }

                        // 3. Bouton Créer
                        Button(
                            onClick = { onCreateGame(isPublic, chosenSide) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentGold,
                                contentColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_create_online_game")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Créer le salon",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenu de l'onglet "Rejoindre une partie" : saisie de code et liste des salons publics.
 */
@Composable
private fun JoinGameTabContent(
    publicGames: List<PublicGameItem>,
    isLoading: Boolean,
    onJoinGame: (code: String) -> Unit
) {
    val colors = LocalWaliColors.current
    var codeInput by remember { mutableStateOf("") }
    val isValid = WaliOnlineCodeGenerator.isValidCode(codeInput)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section 1 : Rejoindre par code
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Rejoindre avec un code secret",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )

                    Text(
                        text = "Entrez le code à 6 caractères partagé par votre adversaire :",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { input ->
                                if (input.length <= 6) {
                                    codeInput = input.uppercase().filter { it.isLetterOrDigit() }
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("EX: W7K9M2") },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isValid && !isLoading) {
                                        onJoinGame(codeInput)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentGold,
                                unfocusedBorderColor = colors.surfaceAlt
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_game_code")
                        )

                        Button(
                            onClick = { onJoinGame(codeInput) },
                            enabled = isValid && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentGold,
                                contentColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("btn_join_by_code")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rejoindre", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2 : Parties publiques en attente
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parties Publiques (${publicGames.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }
        }

        if (publicGames.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Aucune partie publique en attente",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Créez la vôtre pour inviter un adversaire !",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(publicGames, key = { it.code }) { game ->
                PublicGameCard(
                    game = game,
                    onJoin = { onJoinGame(game.code) },
                    isLoading = isLoading
                )
            }
        }
    }
}

/**
 * Carte affichant une partie publique dans la liste.
 */
@Composable
private fun PublicGameCard(
    game: PublicGameItem,
    onJoin: () -> Unit,
    isLoading: Boolean
) {
    val colors = LocalWaliColors.current
    val yourSide = game.creatorSide.opponent
    val sideLabel = if (yourSide == Player.STICKS) "BÂTONNETS" else "CAILLOUX"
    val creatorSideLabel = if (game.creatorSide == Player.STICKS) "Bâtonnets" else "Cailloux"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_game_card_${game.code}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.surfaceAlt)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Code : ${game.code}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accentGold
                        )
                    )
                }
                Text(
                    text = "Hôte : $creatorSideLabel • Vous jouerez : $sideLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                RuleVariantBadgesRow(
                    mandatoryAlignment = game.mandatoryAlignment,
                    defeatAtTwoPieces = game.defeatAtTwoPieces
                )
                Text(
                    text = formatRelativeTime(game.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary.copy(alpha = 0.8f)
                )
            }

            Button(
                onClick = onJoin,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentEarth,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_join_public_game_${game.code}")
            ) {
                Text("Rejoindre", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Affiche les variantes du salon sous forme d'étiquettes :
 * « Alignement obligatoire », « Défaite à 2 pions », ou « Règles classiques » si aucune active.
 */
@Composable
fun RuleVariantBadgesRow(
    mandatoryAlignment: Boolean,
    defeatAtTwoPieces: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val labels = buildList {
        if (mandatoryAlignment) add("Alignement obligatoire")
        if (defeatAtTwoPieces) add("Défaite à 2 pions")
        if (isEmpty()) add("Règles classiques")
    }
    val hasVariants = mandatoryAlignment || defeatAtTwoPieces

    Row(
        modifier = modifier.testTag("online_rule_variant_badges"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasVariants) colors.accentGold.copy(alpha = 0.18f)
                        else colors.surfaceAlt.copy(alpha = 0.7f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (hasVariants) colors.accentGold.copy(alpha = 0.6f)
                        else colors.gridLine.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasVariants) colors.accentGold else colors.textSecondary,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

/**
 * Puce sélectionnable pour le choix du camp (BÂTONNETS ou CAILLOUX).
 */
@Composable
private fun SelectableSideChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val borderCol = if (isSelected) colors.accentGold else colors.surfaceAlt
    val bgCol = if (isSelected) colors.accentGold.copy(alpha = 0.15f) else colors.background

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgCol)
            .border(1.5.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) colors.accentGold else colors.textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

private fun formatCodeSpaced(code: String): String {
    return code.map { it.toString() }.joinToString(" ")
}

private fun formatRelativeTime(timestamp: Long): String {
    val diffSec = (System.currentTimeMillis() - timestamp) / 1000L
    return when {
        diffSec < 10 -> "Créée à l'instant"
        diffSec < 60 -> "Créée il y a ${diffSec}s"
        diffSec < 3600 -> "Créée il y a ${diffSec / 60} min"
        else -> "Créée il y a ${diffSec / 3600} h"
    }
}
