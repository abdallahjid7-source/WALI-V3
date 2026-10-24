package com.example.wali.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WaliBoardBorder
import com.example.ui.theme.WaliCaptureOrange
import com.example.ui.theme.WaliCellBackground
import com.example.ui.theme.WaliCellBackgroundAlt
import com.example.ui.theme.WaliEarth
import com.example.ui.theme.WaliEarthDark
import com.example.ui.theme.WaliGold
import com.example.ui.theme.WaliGridLine
import com.example.ui.theme.WaliHighlightMove
import com.example.ui.theme.WaliSandLight
import com.example.ui.theme.WaliStoneDark
import com.example.wali.model.Player
import com.example.wali.ui.components.PieceView
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Composants graphiques déclaratifs illustrant les règles officielles du Wali Songhaï.
 * Tous les schémas utilisent la numérotation universelle de 1 à 30 du plateau 5x6.
 */

/**
 * Schéma 1 : Le plateau 5x6 avec numérotation 1 à 30 et repères A-F / 1-5.
 */
@Composable
fun BoardOrientationDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Repères colonnes A à F
            Row(
                modifier = Modifier.width(240.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.width(20.dp))
                listOf("A", "B", "C", "D", "E", "F").forEach { letter ->
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Grille 5 lignes x 6 colonnes
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Repères lignes 1 à 5
                Column(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..5).forEach { row ->
                        Text(
                            text = "$row",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.width(20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Grille miniature 5x6 avec numéros 1 à 30
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WaliBoardBorder)
                        .padding(2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        repeat(5) { r ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                repeat(6) { c ->
                                    val cellNumber = r * 6 + c + 1
                                    val isEven = (r + c) % 2 == 0
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .padding(0.5.dp)
                                            .background(if (isEven) WaliCellBackground else WaliCellBackgroundAlt),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$cellNumber",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = WaliEarthDark.copy(alpha = 0.75f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "30 cases numérotées de 1 à 30 • 5 lignes (1-5) et 6 colonnes (A-F)\nCase 1 = A1 • Case 6 = F1 • Case 25 = A5 • Case 30 = F5",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 2 : Les deux camps (12 Bâtonnets vs 12 Cailloux).
 */
@Composable
fun PiecesStartingDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Camp Bâtonnets
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaliCellBackground)
                    .border(1.dp, WaliGridLine),
                contentAlignment = Alignment.Center
            ) {
                PieceView(player = Player.STICKS)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "12 Bâtonnets",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Text(
                text = "Pose toujours en 1er",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
        }

        Text(
            text = "VS",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colors.accentGold)
        )

        // Camp Cailloux
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaliCellBackground)
                    .border(1.dp, WaliGridLine),
                contentAlignment = Alignment.Center
            ) {
                PieceView(player = Player.STONES)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "12 Cailloux",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Text(
                text = "Second joueur",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp)
            )
        }
    }
}

/**
 * Schéma 3 : Interdiction formelle d'aligner 3 pions en phase de pose.
 */
@Composable
fun PlacementForbiddenRuleDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Case 8 : Bâtonnet posé
                NumberedCellBox(number = 8, player = Player.STICKS)

                // Case 9 : Bâtonnet posé
                NumberedCellBox(number = 9, player = Player.STICKS)

                // Case 10 : 3ème case INTERDITE en pose !
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFEBEE))
                        .border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "10",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Pose interdite",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "INTERDICTION ABSOLUE EN POSE : poser sur la case 10 formerait 3 pions consécutifs [8 - 9 - 10]. Ce coup est illégal et rejeté par le jeu.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 4 : Déplacement orthogonal d'une case.
 */
@Composable
fun MovementOrthogonalDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Grille 3x3 centrée sur la case 15
            Column(
                modifier = Modifier
                    .size(144.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaliBoardBorder)
                    .padding(2.dp)
            ) {
                // Ligne du haut : [Diag interdite] [Haut case 9] [Diag interdite]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ForbiddenCell(modifier = Modifier.weight(1f))
                    AllowedMoveCell(number = 9, direction = "UP", modifier = Modifier.weight(1f))
                    ForbiddenCell(modifier = Modifier.weight(1f))
                }
                // Ligne du milieu : [Gauche case 14] [PION en case 15] [Droite case 16]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AllowedMoveCell(number = 14, direction = "LEFT", modifier = Modifier.weight(1f))
                    NumberedCenterPieceCell(number = 15, player = Player.STONES, modifier = Modifier.weight(1f))
                    AllowedMoveCell(number = 16, direction = "RIGHT", modifier = Modifier.weight(1f))
                }
                // Ligne du bas : [Diag interdite] [Bas case 21] [Diag interdite]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ForbiddenCell(modifier = Modifier.weight(1f))
                    AllowedMoveCell(number = 21, direction = "DOWN", modifier = Modifier.weight(1f))
                    ForbiddenCell(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Un pion en case 15 peut se déplacer vers 9 (haut), 21 (bas), 14 (gauche) ou 16 (droite) si la case est vide.\nDiagonales et sauts par-dessus d'autres pions formellement interdits.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 5 : Alignement de 3 valide (qui déclenche une capture).
 */
@Composable
fun ValidThreeLineDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Alignement de 3 valide : cases 8, 9, 10
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .border(2.dp, colors.accentGold, RoundedCornerShape(8.dp))
                            .background(colors.accentGold.copy(alpha = 0.15f))
                            .padding(4.dp)
                    ) {
                        NumberedCellBox(number = 8, player = Player.STICKS)
                        NumberedCellBox(number = 9, player = Player.STICKS)
                        NumberedCellBox(number = 10, player = Player.STICKS)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cases 8 - 9 - 10 : Ligne valide !",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 10.sp
                        )
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.accentGold
                )

                // 1 pion adverse capturé au choix
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, WaliCaptureOrange, RoundedCornerShape(6.dp))
                            .background(WaliCaptureOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PieceView(player = Player.STONES, isCapturable = true)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1 pion adverse retiré",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = WaliCaptureOrange,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Exactement 3 pions consécutifs = Déclenche le retrait d'un pion adverse au choix.",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 6 : Alignement de 4 pions qui ne vaut rien (0 capture).
 */
@Composable
fun InvalidFourLineDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(1.5.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFEBEE))
                    .padding(4.dp)
            ) {
                NumberedCellBox(number = 13, player = Player.STICKS)
                NumberedCellBox(number = 14, player = Player.STICKS)
                NumberedCellBox(number = 15, player = Player.STICKS)
                NumberedCellBox(number = 16, player = Player.STICKS)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "4 ou 5 pions alignés = AUCUNE capture (0 ligne) !",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RÈGLE MAJEURE : Les alignements de 4 ou 5 pions consécutifs (ex: cases 13-14-15-16) sont nuls et non avenus. Ils ne constituent pas une ligne et ne rapportent aucune capture.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 7 : Le Cheval Simple (un déplacement qui ferme 1 ligne = 1 capture).
 */
@Composable
fun SimpleChevalDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Exemple : Pion déplacé de 9 vers 15",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Représentation de la zone : case 9 au-dessus, et ligne 14, 15, 16
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Case 9
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentGold.copy(alpha = 0.2f))
                        .border(1.dp, colors.accentGold),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("9", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = colors.textSecondary))
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.accentGold, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rangée 14, 15, 16
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberedCellBox(number = 14, player = Player.STICKS)
                    // Case 15 d'arrivée
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WaliGold.copy(alpha = 0.25f))
                            .border(2.dp, WaliGold, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("15", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            PieceView(player = Player.STICKS)
                        }
                    }
                    NumberedCellBox(number = 16, player = Player.STICKS)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Le pion glisse de la case 9 vers la case 15 pour compléter [14 - 15 - 16].\nDonne droit à 1 capture immédiate.",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 8 : Le Cheval Double (2 lignes formées en 1 coup = 1 SEULE capture !).
 * Configuration géométriquement légale :
 * Le pion part de la case 9 et glisse vers la case 15 (la case 9 se vide).
 * Cela complète simultanément :
 * - La ligne horizontale [13 - 14 - 15]
 * - La ligne verticale [15 - 21 - 27]
 * La case 9 (en haut) et la case 16 (à droite) sont libres.
 */
@Composable
fun DoubleChevalDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Croisement en L (Cheval double)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.accentGold
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Coup joué : déplacement de la case 9 vers la case 15",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Disposition géométrique réelle :
            // Rangée 2 : Case 9 (Départ -> se vide)
            // Rangée 3 : Cases 13 (STICKS), 14 (STICKS), 15 (Arrivée pivot STICKS), 16 (libre)
            // Rangée 4 : Case 21 (STICKS)
            // Rangée 5 : Case 27 (STICKS)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Rangée du haut avec case 9 de départ
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(modifier = Modifier.width(76.dp)) // Aligne sur la colonne de 15
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accentGold.copy(alpha = 0.15f))
                            .border(1.dp, colors.accentGold, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("9", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, color = colors.textSecondary))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.accentGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Rangée centrale : 13 - 14 - 15 (Arrivée) - 16 (vide)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberedCellBox(number = 13, player = Player.STICKS, size = 36)
                    NumberedCellBox(number = 14, player = Player.STICKS, size = 36)

                    // Case pivot 15 (arrivée)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(WaliGold.copy(alpha = 0.35f))
                            .border(2.dp, WaliGold, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("15", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            PieceView(player = Player.STICKS)
                        }
                    }

                    // Case 16 (libre)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(WaliCellBackground)
                            .border(0.5.dp, WaliGridLine, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("16", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, color = colors.textSecondary.copy(alpha = 0.6f)))
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Rangée suivante : Case 21
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(modifier = Modifier.width(76.dp))
                    NumberedCellBox(number = 21, player = Player.STICKS, size = 36)
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Rangée suivante : Case 27
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(modifier = Modifier.width(76.dp))
                    NumberedCellBox(number = 27, player = Player.STICKS, size = 36)
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentGold.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Ligne horizontale [13-14-15] + Ligne verticale [15-21-27] fermées en même temps\n= UNE SEULE capture autorisée pour ce coup !",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Schéma 9 : Réarmement d'une ligne consommée (casser puis reformer).
 */
@Composable
fun UsedLineAndResetDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Casser la ligne (ex: pion en 10 part vers 16)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "1. Casser",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        NumberedCellBox(number = 8, player = Player.STICKS, size = 30)
                        NumberedCellBox(number = 9, player = Player.STICKS, size = 30)
                        // Case 10 vide (le pion est sorti)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFEBEE))
                                .border(1.dp, Color(0xFFD32F2F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("10", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, color = Color(0xFFD32F2F)))
                        }
                    }
                    Text("Ligne rompue", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = colors.textSecondary))
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.accentGold,
                    modifier = Modifier.size(16.dp)
                )

                // 2. Reformer la ligne (le pion revient en 10)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "2. Reformer",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.accentGold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        NumberedCellBox(number = 8, player = Player.STICKS, size = 30)
                        NumberedCellBox(number = 9, player = Player.STICKS, size = 30)
                        NumberedCellBox(number = 10, player = Player.STICKS, size = 30, isGold = true)
                    }
                    Text("Réarmée (+1 capture)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = colors.accentGold, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tant qu'une ligne de 3 reste inchangée, elle est inactive (utilisée).\nPour capturer à nouveau avec elle, il faut obligatoirement la casser puis la reformer.",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 10 : Les façons de gagner (élimination, blocage, variante 2 pions).
 */
@Composable
fun VictoryConditionsDiagram(modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Condition A : Élimination
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Élimination",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Text(
                text = "0 pion adverse restant",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }

        // Condition B : Blocage
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Blocage",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Text(
                text = "Adversaire sans coup légal",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }

        // Condition C : Variante 2 pions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "2",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Option 2 pions",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Text(
                text = "Adversaire réduit à 2 pions",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// Composants internes d'assistance pour le dessin des cases
// -------------------------------------------------------------

@Composable
private fun NumberedCellBox(
    number: Int,
    player: Player? = null,
    size: Int = 44,
    isGold: Boolean = false
) {
    val colors = LocalWaliColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isGold) colors.accentGold.copy(alpha = 0.25f) else WaliCellBackground)
            .border(if (isGold) 1.5.dp else 1.dp, if (isGold) colors.accentGold else WaliGridLine, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (size < 36) 7.sp else 8.sp,
                    color = WaliEarthDark.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            )
            if (player != null) {
                PieceView(player = player)
            }
        }
    }
}

@Composable
private fun NumberedCenterPieceCell(number: Int, player: Player, modifier: Modifier = Modifier) {
    val colors = LocalWaliColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .background(WaliCellBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, color = WaliEarthDark.copy(alpha = 0.7f))
            )
            PieceView(player = player)
        }
    }
}

@Composable
private fun AllowedMoveCell(number: Int, direction: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .background(WaliHighlightMove.copy(alpha = 0.2f))
            .border(1.dp, WaliHighlightMove, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, color = WaliHighlightMove, fontWeight = FontWeight.Bold)
            )
            val icon = when (direction) {
                "UP" -> Icons.Default.KeyboardArrowUp
                "DOWN" -> Icons.Default.KeyboardArrowDown
                "LEFT" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                else -> Icons.AutoMirrored.Filled.KeyboardArrowRight
            }
            Icon(imageVector = icon, contentDescription = "Déplacement autorisé", tint = WaliHighlightMove, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ForbiddenCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .background(Color(0xFFEEEEEE)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Interdit", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
    }
}
