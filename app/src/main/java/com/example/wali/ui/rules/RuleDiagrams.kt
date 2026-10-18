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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import com.example.ui.theme.WaliSandBackground
import com.example.ui.theme.WaliSandLight
import com.example.ui.theme.WaliStoneDark
import com.example.wali.model.Player
import com.example.wali.ui.components.PieceView

/**
 * Composants graphiques Jetpack Compose illustrant les règles du Wali Songhaï.
 * Réalisés 100% en code déclaratif sans aucune ressource externe.
 */

/**
 * Schéma 1 : Plateau 5x6 et repères A-F / 1-5.
 */
@Composable
fun BoardOrientationDiagram(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Repères A à F
            Row(
                modifier = Modifier.width(220.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                listOf("A", "B", "C", "D", "E", "F").forEach { letter ->
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = WaliEarthDark,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grille 5 lignes x 6 colonnes
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Numéros de lignes 1 à 5
                Column(
                    modifier = Modifier.height(130.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..5).forEach { row ->
                        Text(
                            text = "$row",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = WaliEarthDark,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.width(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Grille miniature 5x6
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 130.dp)
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
                                    val isEven = (r + c) % 2 == 0
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .padding(0.5.dp)
                                            .background(if (isEven) WaliCellBackground else WaliCellBackgroundAlt)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "30 cases gravées dans le sable • 6 colonnes (A à F) et 5 lignes (1 à 5)",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = WaliStoneDark,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 2 : Les deux camps (Bâtonnets et Cailloux).
 */
@Composable
fun PiecesStartingDiagram(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
                    .size(54.dp)
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark)
            )
            Text(
                text = "Commence en 1er",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
        }

        Text(
            text = "VS",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = WaliEarth)
        )

        // Camp Cailloux
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark)
            )
            Text(
                text = "Deuxième joueur",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliStoneDark, fontSize = 11.sp)
            )
        }
    }
}

/**
 * Schéma 3 : Interdiction formelle d'aligner 3 pions en phase de pose.
 */
@Composable
fun PlacementForbiddenRuleDiagram(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Case 1 : Bâtonnet posé
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(WaliCellBackground)
                        .border(1.dp, WaliGridLine),
                    contentAlignment = Alignment.Center
                ) {
                    PieceView(player = Player.STICKS)
                }

                // Case 2 : Bâtonnet posé
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(WaliCellBackground)
                        .border(1.dp, WaliGridLine),
                    contentAlignment = Alignment.Center
                ) {
                    PieceView(player = Player.STICKS)
                }

                // Case 3 : 3ème case INTERDITE en pose !
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFEBEE))
                        .border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Pose interdite",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "INTERDIT en phase de pose : vous ne pouvez jamais former 3 pions consécutifs lors de la pose.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFB71C1C),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 4 : Déplacement orthogonal d'une case (pas de diagonale, pas de saut).
 */
@Composable
fun MovementOrthogonalDiagram(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Grille 3x3 avec pion au centre
            Column(
                modifier = Modifier
                    .size(138.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaliBoardBorder)
                    .padding(2.dp)
            ) {
                // Ligne 1 : [Diagonale interdite] [Haut valide] [Diagonale interdite]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ForbiddenCell(modifier = Modifier.weight(1f))
                    AllowedMoveCell(direction = "UP", modifier = Modifier.weight(1f))
                    ForbiddenCell(modifier = Modifier.weight(1f))
                }
                // Ligne 2 : [Gauche valide] [PION AU CENTRE] [Droite valide]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AllowedMoveCell(direction = "LEFT", modifier = Modifier.weight(1f))
                    CenterPieceCell(player = Player.STONES, modifier = Modifier.weight(1f))
                    AllowedMoveCell(direction = "RIGHT", modifier = Modifier.weight(1f))
                }
                // Ligne 3 : [Diagonale interdite] [Bas valide] [Diagonale interdite]
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ForbiddenCell(modifier = Modifier.weight(1f))
                    AllowedMoveCell(direction = "DOWN", modifier = Modifier.weight(1f))
                    ForbiddenCell(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Une case à la fois • Horizontal ou vertical uniquement • Diagonales et sauts interdits",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = WaliStoneDark,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CenterPieceCell(player: Player, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .background(WaliCellBackground),
        contentAlignment = Alignment.Center
    ) {
        PieceView(player = player)
    }
}

@Composable
private fun AllowedMoveCell(direction: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .background(WaliHighlightMove.copy(alpha = 0.2f))
            .border(1.5.dp, WaliHighlightMove, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        val icon = when (direction) {
            "UP" -> Icons.Default.KeyboardArrowUp
            "DOWN" -> Icons.Default.KeyboardArrowDown
            "LEFT" -> Icons.Default.KeyboardArrowLeft
            else -> Icons.Default.KeyboardArrowRight
        }
        Icon(imageVector = icon, contentDescription = "Déplacement autorisé", tint = WaliHighlightMove, modifier = Modifier.size(22.dp))
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
        Icon(imageVector = Icons.Default.Close, contentDescription = "Diagonale interdite", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
    }
}

/**
 * Schéma 5 : Formation d'une ligne de trois et capture du pion adverse.
 */
@Composable
fun LineFormationCaptureDiagram(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ligne de 3 formée
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .border(2.dp, WaliGold, RoundedCornerShape(8.dp))
                            .background(WaliGold.copy(alpha = 0.15f))
                            .padding(4.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WaliCellBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                PieceView(player = Player.STICKS)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3 pions alignés !",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark, fontSize = 10.sp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = WaliEarth
                )

                // Pion adverse capturé au choix
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.5.dp, WaliCaptureOrange, RoundedCornerShape(6.dp))
                            .background(WaliCaptureOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PieceView(player = Player.STONES, isCapturable = true)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1 pion capturé",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliCaptureOrange, fontSize = 10.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aligner 3 pions (horizontal ou vertical) permet de capturer n'importe quel pion adverse.",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliStoneDark, fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Schéma 6 : Réutilisation d'une ligne (casser puis reformer).
 */
@Composable
fun LineReuseDiagram(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Étape 1 : Casser la ligne
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "1. Casser",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MiniPieceCell(player = Player.STICKS)
                        MiniPieceCell(player = Player.STICKS)
                        EmptyMiniCell(isBreaking = true)
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = WaliEarth,
                    modifier = Modifier.size(18.dp)
                )

                // Étape 2 : Reformer la ligne
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "2. Reformer",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliGold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MiniPieceCell(player = Player.STICKS)
                        MiniPieceCell(player = Player.STICKS)
                        MiniPieceCell(player = Player.STICKS, isGold = true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Une ligne déjà utilisée doit être rompue puis reformée pour capturer à nouveau.\nUne nouvelle ligne formée ailleurs capture sans attendre.",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliStoneDark, fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MiniPieceCell(player: Player, isGold: Boolean = false) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isGold) WaliGold.copy(alpha = 0.2f) else WaliCellBackground)
            .border(if (isGold) 1.5.dp else 0.5.dp, if (isGold) WaliGold else WaliGridLine),
        contentAlignment = Alignment.Center
    ) {
        PieceView(player = player)
    }
}

@Composable
private fun EmptyMiniCell(isBreaking: Boolean = false) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(WaliCellBackgroundAlt)
            .border(1.dp, if (isBreaking) Color(0xFFD32F2F) else WaliGridLine, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isBreaking) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Schéma 7 : Les deux façons de gagner (élimination et blocage).
 */
@Composable
fun VictoryConditionsDiagram(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaliSandLight)
            .border(1.5.dp, WaliEarth.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
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
                    .size(44.dp)
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
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark)
            )
            Text(
                text = "Tous les pions de l'adversaire sont capturés",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliStoneDark, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Condition B : Blocage
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Blocage",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = WaliEarthDark)
            )
            Text(
                text = "L'adversaire n'a plus aucun coup légal possible",
                style = MaterialTheme.typography.bodySmall.copy(color = WaliStoneDark, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}
