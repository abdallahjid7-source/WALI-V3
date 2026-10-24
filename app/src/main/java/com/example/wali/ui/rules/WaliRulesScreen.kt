package com.example.wali.ui.rules

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Écran d'aide et de règles complet "Comment jouer au Wali ?"
 * Organisé en 4 onglets structurés, sans aucun jargon technique, illustré de schémas interactifs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliRulesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        RuleTabItem("Bases", Icons.Default.SportsEsports, "tab_rules_basics"),
        RuleTabItem("Captures", Icons.Default.EmojiEvents, "tab_rules_captures"),
        RuleTabItem("Points & Variantes", Icons.Default.Tune, "tab_rules_scores"),
        RuleTabItem("Modes & En ligne", Icons.Default.Public, "tab_rules_modes")
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("rules_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Règles du Wali",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = "Jeu traditionnel Songhaï de Tombouctou",
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
                            .testTag("btn_rules_back")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barre d'onglets ergonomique 4 onglets
            androidx.compose.material3.TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = colors.background,
                contentColor = colors.accentGold,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = colors.accentGold
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier
                            .testTag(tab.testTag)
                            .height(48.dp),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isSelected) colors.accentGold else colors.textSecondary
                                )
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.accentGold else colors.textSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    )
                }
            }

            // Contenu de l'onglet actif (réinitialisé en haut à chaque changement d'onglet)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                androidx.compose.runtime.key(selectedTabIndex) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("rules_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (selectedTabIndex) {
                            0 -> rulesBasicsTab(onNavigateBack = onNavigateBack)
                            1 -> rulesCapturesTab(onNavigateBack = onNavigateBack)
                            2 -> rulesScoresAndVariantsTab(onNavigateBack = onNavigateBack)
                            3 -> rulesModesAndOnlineTab(onNavigateBack = onNavigateBack)
                        }
                    }
                }
            }
        }
    }
}

private data class RuleTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

// =========================================================================
// ONGLET 1 : RÈGLES DE BASE (Accessible immédiatement à un débutant)
// =========================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.rulesBasicsTab(
    onNavigateBack: () -> Unit
) {
    // 1. Objectif du jeu en deux phrases
    item {
        IntroGoalCard()
    }

    // 2. Le plateau 5x6 et les 30 cases numérotées
    item {
        RuleCard(
            badge = "1",
            title = "Le Plateau et ses 30 Cases",
            description = "Le Wali se dispute sur une grille de 5 lignes et 6 colonnes, soit 30 cases numérotées de 1 à 30 (de gauche à droite et de haut en bas). La numérotation est fixe et universelle pour les deux joueurs."
        ) {
            BoardOrientationDiagram()
        }
    }

    // 3. Les deux camps et le premier coup
    item {
        RuleCard(
            badge = "2",
            title = "Les Pions et le Premier Coup",
            description = "Chaque camp dispose de 12 pièces : 12 Bâtonnets de bois contre 12 Cailloux. Les BÂTONNETS jouent TOUJOURS en premier."
        ) {
            PiecesStartingDiagram()
        }
    }

    // 4. Phase 1 : La pose et interdiction formelle d'aligner
    item {
        RuleCard(
            badge = "3",
            title = "Phase 1 : La Pose des Pions",
            description = "À tour de rôle, chaque joueur pose un pion sur une case vide du plateau. Aucune capture n'a lieu pendant cette phase."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlacementForbiddenRuleDiagram()

                NoticeBanner(
                    icon = Icons.Default.Warning,
                    title = "Interdiction Absolue en Pose",
                    message = "Il est STRICTEMENT INTERDIT de poser un pion sur une case si cela forme immédiatement un alignement de 3 pions consécutifs. Ce coup est bloqué par le jeu.",
                    isCritical = true
                )
            }
        }
    }

    // 5. La pose différée (pions en attente)
    item {
        RuleCard(
            badge = "4",
            title = "La Pose Différée (Pions en Attente)",
            description = "Si un joueur ne dispose plus d'aucune case légale en fin de pose (toutes les cases vides restantes lui feraient former un 3-en-ligne interdit), la pose s'arrête immédiatement : ses pions restants sont placés en réserve d'attente."
        ) {
            NoticeBanner(
                icon = Icons.Default.Info,
                title = "Obligation de Pose en Phase 2",
                message = "Dès le début de la phase de déplacement, si vous possédez des pions en réserve, vous avez l'OBLIGATION ABSOLUE d'en poser un dès qu'une case légale est disponible. Aucun déplacement n'est permis tant que vous devez poser vos pions en attente.",
                isCritical = false
            )
        }
    }

    // 6. Phase 2 : Le déplacement orthogonal
    item {
        RuleCard(
            badge = "5",
            title = "Phase 2 : Le Déplacement",
            description = "Une fois les pions posés, commence la phase de déplacement. Les Bâtonnets jouent le premier coup (sauf si un blocage de pose a donné la main au joueur bloqué). À votre tour, vous glissez un pion d'exactement une case vers une case voisine libre."
        ) {
            MovementOrthogonalDiagram()
        }
    }

    item {
        PlayActionButton(onNavigateBack = onNavigateBack)
    }
}

// =========================================================================
// ONGLET 2 : CAPTURES & VICTOIRE
// =========================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.rulesCapturesTab(
    onNavigateBack: () -> Unit
) {
    // 1. Alignement de 3 valide
    item {
        RuleCard(
            badge = "A",
            title = "Alignement Valide (Le \"Cheval\")",
            description = "Former un alignement d'exactement 3 pions consécutifs (horizontal ou vertical) permet de capturer et retirer un pion adverse de votre choix sur le plateau."
        ) {
            ValidThreeLineDiagram()
        }
    }

    // 2. Règle stricte des 4 ou 5 pions
    item {
        RuleCard(
            badge = "B",
            title = "Alignements de 4 ou 5 Pions : NULS !",
            description = "Au Wali, la règle est stricte : seuls les alignements d'EXACTEMENT 3 pions consécutifs sont pris en compte."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InvalidFourLineDiagram()

                NoticeBanner(
                    icon = Icons.Default.Warning,
                    title = "Attention à l'Alignement Long",
                    message = "Avoir 4 ou 5 pions alignés ne constitue PAS une ligne. Cela ne donne droit à AUCUNE capture. Si vous allongez une ligne de 3 pour en faire 4, vous perdez la ligne !",
                    isCritical = true
                )
            }
        }
    }

    // 3. Cheval simple et cheval double
    item {
        RuleCard(
            badge = "C",
            title = "Cheval Simple et Cheval Double",
            description = "Un « Cheval simple » ferme 1 ligne de trois. Un « Cheval double » survient lorsqu'un pion pivot ferme simultanément deux lignes (croisement en L ou en T)."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SimpleChevalDiagram()
                Spacer(modifier = Modifier.height(2.dp))
                DoubleChevalDiagram()

                NoticeBanner(
                    icon = Icons.Default.Info,
                    title = "Une Seule Capture par Coup !",
                    message = "RÈGLE AUTHENTIQUE : Même lors d'un « Cheval double » qui forme deux lignes d'un coup, le joueur n'a droit qu'à UNE SEULE capture au total.",
                    isCritical = false
                )
            }
        }
    }

    // 4. Les captures : choix libre et sans immunité
    item {
        RuleCard(
            badge = "D",
            title = "Règles de Capture de la Cible",
            description = "Lorsqu'une capture est déclenchée, vous retirez exactement 1 pion adverse.\n• Choix libre : vous choisissez le pion adverse que vous voulez partout sur le plateau.\n• Aucune immunité : un pion adverse qui fait partie d'un alignement ennemi peut être capturé sans restriction.\n• Le tour ne passe à l'adversaire qu'une fois votre cible sélectionnée."
        )
    }

    // 5. Réarmement d'une ligne consommée (usedLines)
    item {
        RuleCard(
            badge = "E",
            title = "Réarmement d'une Ligne Déjà Utilisée",
            description = "Une ligne de 3 ayant servi à capturer est enregistrée comme « consommée ». Elle ne donne plus droit à aucune capture tant qu'elle reste intacte."
        ) {
            UsedLineAndResetDiagram()
        }
    }

    // 6. Comment gagner la partie
    item {
        RuleCard(
            badge = "F",
            title = "Conditions de Victoire",
            description = "La partie se termine immédiatement dès qu'un joueur l'emporte selon l'une des conditions suivantes :"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VictoryConditionsDiagram()

                Text(
                    text = "• Par Élimination : tous les pions adverses sont capturés (l'adversaire a 0 pion restant).\n• Par Blocage : l'adversaire n'a plus aucun coup légal possible au début de son tour (toutes ses pièces sont coincées).\n• Par Forfait : l'adversaire abandonne ou est déclaré absent après inactivité prolongée.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                )
            }
        }
    }

    item {
        PlayActionButton(onNavigateBack = onNavigateBack)
    }
}

// =========================================================================
// ONGLET 3 : SCORES, MATCH NUL & VARIANTES
// =========================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.rulesScoresAndVariantsTab(
    onNavigateBack: () -> Unit
) {
    // 1. Décompte des points et victoire écrasante
    item {
        RuleCard(
            badge = "1",
            title = "Décompte des Points & Victoire Écrasante",
            description = "Le système de score récompense la domination tactique :"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .border(1.dp, Color(0xFF2E7D32))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "+1 Point — Victoire Standard",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                        )
                        Text(
                            text = "Attribué lorsque vous gagnez et que l'adversaire a réussi à faire au moins une capture.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF2E7D32), fontSize = 11.sp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF8E1))
                        .border(1.5.dp, Color(0xFFFFA000))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "+2 Points — Victoire Écrasante !",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        )
                        Text(
                            text = "Attribué si le perdant n'a capturé AUCUN pion adverse de toute la partie (0 capture). Cette prime de 2 points s'applique également si l'adversaire abandonne ou se déconnecte sans avoir capturé le moindre pion.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB78103), fontSize = 11.sp)
                        )
                    }
                }

                NoticeBanner(
                    icon = Icons.Default.Info,
                    title = "Impact du Match Nul sur la Série",
                    message = "Un match nul ne rapporte aucun point (0 pt) à aucun des deux joueurs et INTERROMPT la série de victoires en cours (remise à zéro de votre streak).",
                    isCritical = false
                )
            }
        }
    }

    // 2. Le match nul
    item {
        RuleCard(
            badge = "2",
            title = "Proposition de Match Nul",
            description = "Deux joueurs humains peuvent convenir d'un match nul sous des conditions strictes :\n\n• Uniquement en phase de déplacement (jamais pendant la pose).\n• Jamais contre l'ordinateur (l'IA ne négocie pas de nul).\n• Uniquement pendant son tour de jeu, avant de déplacer un pion.\n• Impossible si une capture ou une pose obligatoire est en attente.\n• En cas de refus de l'adversaire : vous ne perdez pas votre tour. Vous devez jouer votre coup, mais vous ne pouvez pas reproposer de match nul avant d'avoir joué au moins un nouveau coup."
        )
    }

    // 3. Les variantes de règles
    item {
        RuleCard(
            badge = "3",
            title = "Variantes de Règles Optionnelles",
            description = "Deux variantes officielles peuvent être activées dans les Réglages (icône d'engrenage) sous la section « Variantes de règles » :"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalWaliColors.current.surface)
                        .border(1.dp, LocalWaliColors.current.accentGold.copy(alpha = 0.3f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "1. Alignement Obligatoire",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = LocalWaliColors.current.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Si un joueur dispose d'au moins un déplacement formant un alignement capturant, il a l'obligation stricte de jouer l'un de ces coups. Les déplacements ordinaires sont désactivés.",
                            style = MaterialTheme.typography.bodySmall.copy(color = LocalWaliColors.current.textSecondary, fontSize = 11.sp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalWaliColors.current.surface)
                        .border(1.dp, LocalWaliColors.current.accentGold.copy(alpha = 0.3f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "2. Défaite à Deux Pions",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = LocalWaliColors.current.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dès qu'un joueur est réduit à 2 pions vivants sur le plateau en phase de déplacement, il perd immédiatement (ne pouvant plus mathématiquement former de ligne de 3).",
                            style = MaterialTheme.typography.bodySmall.copy(color = LocalWaliColors.current.textSecondary, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }

    item {
        PlayActionButton(onNavigateBack = onNavigateBack)
    }
}

// =========================================================================
// ONGLET 4 : MODES DE JEU, CHRONO & EN LIGNE
// =========================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.rulesModesAndOnlineTab(
    onNavigateBack: () -> Unit
) {
    // 1. Les trois modes de jeu
    item {
        RuleCard(
            badge = "1",
            title = "Les Modes de Jeu",
            description = "Le Wali propose 3 modes complets de jeu :"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "• Deux joueurs sur le même écran : Disposition face-à-face où le plateau est orienté pour les deux personnes assises l'une en face de l'autre. Les Bâtonnets sont en haut (inversés à 180°), les Cailloux en bas, avec leurs propres boutons de commande indépendants.\n\n• Contre la Machine : Affrontez l'ordinateur selon 3 niveaux de difficulté :\n  - Facile : joue rapidement et commet des erreurs tactiques pour débuter sereinement.\n  - Moyen : anticipe quelques coups à l'avance et exploite vos ouvertures.\n  - Difficile : réfléchit longuement, anticipe très loin et ne fait aucun cadeau. Un adversaire redoutable !\n\n• Multijoueur en ligne : Créez ou rejoignez un salon privé avec un code d'invitation pour défier vos amis à distance.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                )
            }
        }
    }

    // 2. Mode Spectateur & Chat
    item {
        RuleCard(
            badge = "2",
            title = "Mode Spectateur et Chat de Salon",
            description = "• Code Spectateur : Vous pouvez inviter des amis à observer la partie grâce à un code spectateur dédié. Le spectateur suit le match en direct sans risquer de déplacer de pièce par mégarde.\n• Chat en direct : Joueurs et spectateurs peuvent discuter et envoyer des messages et réactions pendant toute la durée de la partie."
        )
    }

    // 3. Chronomètre de coup & Expiration
    item {
        RuleCard(
            badge = "3",
            title = "Le Chronomètre de Coup",
            description = "Dans les Réglages, vous pouvez activer le chronomètre et choisir une cadence (15s, 30s, 45s ou 60s par coup)."
        ) {
            NoticeBanner(
                icon = Icons.Default.Info,
                title = "Que se passe-t-il à l'expiration du temps ?",
                message = "Vous NE PERDEZ PAS la partie au temps ! Si votre chronomètre expire :\n• Si une capture était en attente : un pion adverse est choisi et capturé automatiquement au hasard.\n• En phase de pose : un pion est posé automatiquement sur une case légale au hasard.\n• En déplacement : un coup légal est joué automatiquement au hasard.",
                isCritical = false
            )
        }
    }

    // 4. Abandon et déconnexion en ligne
    item {
        RuleCard(
            badge = "4",
            title = "Abandon & Déconnexion en Ligne",
            description = "Pour garantir le respect du temps de chacun en multijoueur :\n• Après 20 secondes d'inactivité pendant le tour adverse : un indicateur de connexion instable s'affiche.\n• Après 120 secondes (2 minutes) consécutives d'inactivité : le bouton « Déclarer l'adversaire abandonnataire » apparaît. En cliquant dessus, vous remportez la victoire par forfait immédiat."
        )
    }

    // 5. Revanche et suivi des scores du salon
    item {
        RuleCard(
            badge = "5",
            title = "Revanche & Historique du Salon",
            description = "À la fin de chaque partie en ligne, vous pouvez appuyer sur « Revanche » pour enchaîner immédiatement une nouvelle manche dans le même salon. Les scores cumulés de la session entre les deux joueurs sont conservés et affichés en temps réel."
        )
    }

    item {
        PlayActionButton(onNavigateBack = onNavigateBack)
    }
}

// =========================================================================
// COMPOSANTS GRAPHIQUES DE PRÉSENTATION
// =========================================================================

@Composable
private fun IntroGoalCard() {
    val colors = LocalWaliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Objectif du jeu",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.accentGold
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Au Wali, formez des alignements de trois pions pour capturer les pièces de votre adversaire et éliminez-les toutes. Vous gagnez également la partie si vous parvenez à bloquer tous les pions adverses sans leur laisser le moindre déplacement possible.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textPrimary,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
private fun RuleCard(
    badge: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    val colors = LocalWaliColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        .size(28.dp)
                        .background(colors.accentGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.surface
                        )
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            )

            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun NoticeBanner(
    icon: ImageVector,
    title: String,
    message: String,
    isCritical: Boolean = false
) {
    val bgColor = if (isCritical) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
    val borderColor = if (isCritical) Color(0xFFD32F2F) else Color(0xFFFFA000)
    val titleColor = if (isCritical) Color(0xFFC62828) else Color(0xFFE65100)
    val textColor = if (isCritical) Color(0xFFB71C1C) else Color(0xFF795548)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textColor,
                        lineHeight = 16.sp,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun PlayActionButton(onNavigateBack: () -> Unit) {
    val colors = LocalWaliColors.current
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onNavigateBack,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("btn_rules_bottom_back"),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentEarth,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = colors.accentGold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "J'ai compris, jouer !",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
}
