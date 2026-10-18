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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Écran explicatif complet "Comment jouer au Wali ?"
 * Présente les règles ancestrales du Wali Songhaï de Tombouctou de manière claire,
 * pédagogique et illustrée de mini-schémas Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliRulesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
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
                            contentDescription = "Retour à la partie",
                            tint = colors.textPrimary
                        )
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
                    .testTag("rules_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                IntroCard()
            }

            // 1. Le plateau 5x6
            item {
                RuleSectionCard(
                    number = "1",
                    title = "Le Plateau et l'Orientation",
                    description = "Le jeu se dispute sur une grille de 30 cases (5 lignes numérotées de 1 à 5 et 6 colonnes identifiées de A à F), traditionnellement creusées à même le sable."
                ) {
                    BoardOrientationDiagram()
                }
            }

            // 2. Les pièces et qui commence
            item {
                RuleSectionCard(
                    number = "2",
                    title = "Les Pions et le Premier Coup",
                    description = "Chaque joueur dispose de 12 pièces : 12 bâtonnets de bois pour le premier joueur et 12 cailloux pour le second. Le joueur aux bâtonnets commence TOUJOURS la partie."
                ) {
                    PiecesStartingDiagram()
                }
            }

            // 3. Phase 1 : La pose
            item {
                RuleSectionCard(
                    number = "3",
                    title = "Phase 1 : La Pose des Pions",
                    description = "À tour de rôle, chaque joueur pose un pion sur une case vide. Règle fondamentale : il est STRICTEMENT INTERDIT d'aligner 3 pions consécutifs pendant la phase de pose. Aucune capture n'a lieu pendant cette phase."
                ) {
                    PlacementForbiddenRuleDiagram()
                }
            }

            // 4. Phase 2 : Le déplacement
            item {
                RuleSectionCard(
                    number = "4",
                    title = "Phase 2 : Le Déplacement",
                    description = "Une fois les 24 pions placés, commence la phase de déplacement. À son tour, un joueur fait glisser un pion d'une seule case vers une case voisine libre. Les mouvements se font EXCLUSIVEMENT à l'horizontale ou à la verticale. Les diagonales et les sauts par-dessus un autre pion sont formellement interdits."
                ) {
                    MovementOrthogonalDiagram()
                }
            }

            // 5. La ligne de trois et la capture
            item {
                RuleSectionCard(
                    number = "5",
                    title = "Alignement et Capture (Le \"Cheval\")",
                    description = "Former un alignement de 3 pions de son camp (horizontal ou vertical, appelé « cheval ») donne le droit immédiat de capturer et retirer n'importe quel pion adverse de son choix sur le plateau. Si un déplacement forme simultanément deux lignes de trois (« cheval double »), le joueur effectue deux captures consécutives."
                ) {
                    LineFormationCaptureDiagram()
                }
            }

            // 6. Réutilisation d'une ligne
            item {
                RuleSectionCard(
                    number = "6",
                    title = "Réutilisation d'une Ligne Déjà Formée",
                    description = "Une ligne de trois déjà exploitée pour capturer ne permet pas de capturer à nouveau si elle reste inchangée. Pour capturer à nouveau avec cette ligne, il faut obligatoirement la ROMPRE (sortir un pion) puis la REFORMER plus tard. En revanche, former une autre ligne distincte ailleurs permet de capturer, même si l'ancienne ligne est restée intacte."
                ) {
                    LineReuseDiagram()
                }
            }

            // 7. Conditions de victoire
            item {
                RuleSectionCard(
                    number = "7",
                    title = "Comment Gagner la Partie ?",
                    description = "La partie se termine immédiatement dès qu'un joueur l'emporte selon l'une de ces deux conditions :\n• Par Élimination : tous les pions adverses sont capturés.\n• Par Blocage : l'adversaire n'a plus aucun déplacement possible à son tour (toutes ses pièces sont coincées)."
                ) {
                    VictoryConditionsDiagram()
                }
            }

            // Bouton retour en bas
            item {
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
}

/**
 * Carte d'introduction culturelle et historique.
 */
@Composable
private fun IntroCard() {
    val colors = LocalWaliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Un trésor du désert malien",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pratiqué au Mali et dans toute la boucle du Niger, le Wali est un jeu d'alignement et de blocage réputé pour sa subtilité tactique. Découvrez ci-dessous les règles authentiques pour maîtriser ce jeu ancestral.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

/**
 * Carte structurée pour chaque section de règle.
 */
@Composable
private fun RuleSectionCard(
    number: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    diagram: @Composable () -> Unit
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
                        text = number,
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

            Spacer(modifier = Modifier.height(12.dp))

            diagram()
        }
    }
}
