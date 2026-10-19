package com.example.wali.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.wali.model.PieceMove
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Retourne le numéro absolu (1 à 30) associé à une [Position] du plateau 5×6.
 * Ordre canonique de lecture : ligne par ligne (1 à 5) et colonne par colonne (A à F).
 * Invariant quel que soit le camp du joueur local ou le statut de spectateur.
 * Exemple : A1 -> 1, F1 -> 6, A2 -> 7, F2 -> 12, A5 -> 25, F5 -> 30.
 */
fun cellNumberForPosition(position: Position): Int =
    position.row * Position.COLS + position.col + 1

/**
 * Table de correspondance précalculée (constante) associant chaque [Position] à son numéro (1..30).
 */
val CELL_NUMBER_BY_POSITION: Map<Position, Int> =
    Position.allPositions.associateWith { cellNumberForPosition(it) }

/**
 * Table précalculée des étiquettes textuelles ("1".."30") pour éviter toute allocation lors des recompositions.
 */
private val CELL_NUMBER_LABEL_BY_POSITION: Map<Position, String> =
    Position.allPositions.associateWith { cellNumberForPosition(it).toString() }

@Composable
fun WaliBoardView(
    board: Map<Position, Player?>,
    selectedPosition: Position?,
    highlightedMoves: Set<Position>,
    capturablePositions: Set<Position>,
    onCellClicked: (Position) -> Unit,
    modifier: Modifier = Modifier,
    lastMove: PieceMove? = null,
    showCellNumbers: Boolean = true
) {
    // Transition infinie pour pulsation douce des pions capturables
    val waliColors = LocalWaliColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "capturePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Détection de secours automatique d'un déplacement si non fourni explicitement
    var previousBoard by remember { mutableStateOf<Map<Position, Player?>>(board) }
    var detectedMove by remember { mutableStateOf<PieceMove?>(null) }

    LaunchedEffect(board) {
        if (lastMove == null && previousBoard != board) {
            val fromEntry = previousBoard.entries.firstOrNull { (pos, p) -> p != null && board[pos] == null }
            val toEntry = board.entries.firstOrNull { (pos, p) -> p != null && previousBoard[pos] == null }
            if (fromEntry != null && toEntry != null && fromEntry.value == toEntry.value) {
                detectedMove = PieceMove(
                    from = fromEntry.key,
                    to = toEntry.key,
                    player = toEntry.value!!
                )
            }
        }
        previousBoard = board
    }

    val effectiveLastMove = lastMove ?: detectedMove

    // Animation fluide Compose de transition lors du déplacement
    val animProgress = remember { Animatable(0f) }
    var activeMove by remember { mutableStateOf<PieceMove?>(null) }

    LaunchedEffect(effectiveLastMove?.id) {
        val move = effectiveLastMove ?: return@LaunchedEffect
        activeMove = move
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 280,
                easing = FastOutSlowInEasing
            )
        )
        activeMove = null
    }

    BoxWithConstraints(
        modifier = modifier.padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val cols = Position.COLS.toFloat() // 6
        val rows = Position.ROWS.toFloat() // 5
        val boardRatio = cols / rows // 1.2

        val isVeryCompact = maxWidth < 340.dp || (maxHeight != Dp.Infinity && maxHeight < 280.dp)
        val labelWidth = if (isVeryCompact) 18.dp else 24.dp
        val labelHeight = if (isVeryCompact) 18.dp else 22.dp

        val availWidth = (maxWidth - labelWidth - 8.dp).coerceAtLeast(60.dp)
        val hasFiniteHeight = maxHeight != Dp.Infinity && maxHeight > 80.dp
        val availHeight = if (hasFiniteHeight) (maxHeight - labelHeight - 8.dp).coerceAtLeast(60.dp) else Dp.Infinity

        val boardWidth: Dp
        val boardHeight: Dp

        if (availHeight != Dp.Infinity) {
            val widthCandidate = availHeight * boardRatio
            if (widthCandidate <= availWidth) {
                boardWidth = widthCandidate
                boardHeight = availHeight
            } else {
                boardWidth = availWidth
                boardHeight = availWidth / boardRatio
            }
        } else {
            boardWidth = minOf(availWidth, 600.dp)
            boardHeight = boardWidth / boardRatio
        }

        // Styles de texte des numéros (1..30) mémorisés une seule fois hors de la boucle des 30 cases
        val cellNumberFontSize = if (boardWidth < 280.dp) 8.sp else if (boardWidth < 360.dp) 9.sp else 10.sp
        val baseLabelStyle = MaterialTheme.typography.labelSmall
        val emptyCellNumberStyle = remember(baseLabelStyle, cellNumberFontSize, waliColors.textPrimary) {
            baseLabelStyle.copy(
                fontSize = cellNumberFontSize,
                fontWeight = FontWeight.SemiBold,
                color = waliColors.textPrimary.copy(alpha = 0.50f),
                lineHeight = cellNumberFontSize
            )
        }
        val occupiedCellNumberStyle = remember(baseLabelStyle, cellNumberFontSize, waliColors.textPrimary) {
            baseLabelStyle.copy(
                fontSize = cellNumberFontSize,
                fontWeight = FontWeight.Medium,
                color = waliColors.textPrimary.copy(alpha = 0.36f),
                lineHeight = cellNumberFontSize
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- REPERES DE COLONNES SUPERIEURS (A à F) ---
            Row(
                modifier = Modifier.width(boardWidth + labelWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(labelWidth))
                Row(
                    modifier = Modifier
                        .width(boardWidth)
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (c in 0 until Position.COLS) {
                        val colLetter = ('A'.code + c).toChar().toString()
                        Text(
                            text = colLetter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = waliColors.textSecondary,
                                fontSize = if (boardWidth < 280.dp) 10.sp else if (boardWidth < 360.dp) 11.sp else 13.sp
                            ),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // --- PLATEAU AVEC REPERES DE LIGNES (1 à 5) ---
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repères de lignes (1 à 5) sur la gauche
                Column(
                    modifier = Modifier
                        .width(labelWidth)
                        .height(boardHeight),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (r in 0 until Position.ROWS) {
                        val rowNumber = (r + 1).toString()
                        Text(
                            text = rowNumber,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = waliColors.textSecondary,
                                fontSize = if (boardHeight < 220.dp) 10.sp else if (boardHeight < 280.dp) 11.sp else 13.sp
                            ),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Plateau 5x6 stylisé selon le thème actif avec calcul précis des dimensions de cellules
                Box(
                    modifier = Modifier
                        .size(boardWidth, boardHeight)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(waliColors.boardBorder, waliColors.accentEarth, waliColors.boardBorder)
                            )
                        )
                        .border(3.5.dp, waliColors.boardBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    val cellWidth = (boardWidth - 8.dp) / Position.COLS
                    val cellHeight = (boardHeight - 8.dp) / Position.ROWS

                    // Grille intérieure
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(waliColors.boardBorder)
                    ) {
                        for (row in 0 until Position.ROWS) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                for (col in 0 until Position.COLS) {
                                    val position = Position(row, col)
                                    val cellNumber = CELL_NUMBER_BY_POSITION.getValue(position)
                                    val cellNumberLabel = CELL_NUMBER_LABEL_BY_POSITION.getValue(position)
                                    val piece = board[position]
                                    val isSelected = (position == selectedPosition)
                                    val isHighlightedMove = position in highlightedMoves
                                    val isCapturable = position in capturablePositions
                                    val isLastMoveOrigin = (effectiveLastMove?.from == position)
                                    val isLastMoveDestination = (effectiveLastMove?.to == position)
                                    val isPieceCurrentlyMovingToThis = (activeMove?.to == position)

                                    BoardCell(
                                        position = position,
                                        cellNumber = cellNumber,
                                        cellNumberLabel = cellNumberLabel,
                                        showCellNumber = showCellNumbers,
                                        emptyCellNumberStyle = emptyCellNumberStyle,
                                        occupiedCellNumberStyle = occupiedCellNumberStyle,
                                        piece = piece,
                                        isSelected = isSelected,
                                        isHighlightedMove = isHighlightedMove,
                                        isCapturable = isCapturable,
                                        isLastMoveOrigin = isLastMoveOrigin,
                                        isLastMoveDestination = isLastMoveDestination,
                                        isPieceCurrentlyMovingToThis = isPieceCurrentlyMovingToThis,
                                        pulseAlpha = pulseAlpha,
                                        onCellClicked = { onCellClicked(position) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }

                    // --- COUCHE D'ANIMATION DE DEPLACEMENT FLUIDE (OVERLAY) ---
                    activeMove?.let { move ->
                        val progress = animProgress.value
                        val startX = cellWidth * move.from.col
                        val startY = cellHeight * move.from.row
                        val endX = cellWidth * move.to.col
                        val endY = cellHeight * move.to.row

                        val currentX = lerp(startX, endX, progress)
                        val currentY = lerp(startY, endY, progress)

                        // Arc de levée tactile naturelle (préhension et pose douce)
                        val arc = kotlin.math.sin(progress * Math.PI.toFloat()).coerceIn(0f, 1f)
                        val scale = 1.0f + 0.18f * arc
                        val shadowAlpha = 0.35f * arc

                        Box(
                            modifier = Modifier
                                .size(cellWidth, cellHeight)
                                .offset(x = currentX, y = currentY)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .padding(0.75.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Ombre portée dynamique sous le pion en vol
                            if (arc > 0.04f) {
                                Box(
                                    modifier = Modifier
                                        .size(cellWidth * 0.65f, cellHeight * 0.65f)
                                        .graphicsLayer {
                                            translationY = 10f * arc
                                            alpha = shadowAlpha
                                        }
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                )
                            }

                            PieceView(
                                player = move.player,
                                isSelected = false,
                                isCapturable = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    position: Position,
    cellNumber: Int,
    cellNumberLabel: String,
    showCellNumber: Boolean,
    emptyCellNumberStyle: TextStyle,
    occupiedCellNumberStyle: TextStyle,
    piece: Player?,
    isSelected: Boolean,
    isHighlightedMove: Boolean,
    isCapturable: Boolean,
    isLastMoveOrigin: Boolean,
    isLastMoveDestination: Boolean,
    isPieceCurrentlyMovingToThis: Boolean,
    pulseAlpha: Float,
    onCellClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waliColors = LocalWaliColors.current
    // Alternance subtile de teintes selon le thème
    val isEvenCell = (position.row + position.col) % 2 == 0
    val cellBackground = if (isEvenCell) waliColors.cellBackground else waliColors.cellBackgroundAlt

    // Description textuelle pour lecteurs d'écran (TalkBack) incluant le numéro de case (1..30)
    val accessibilityDescription = buildString {
        when (piece) {
            Player.STICKS -> append("Bâtonnet en case $cellNumber (${position.algebraic})")
            Player.STONES -> append("Caillou en case $cellNumber (${position.algebraic})")
            null -> append("Case $cellNumber (${position.algebraic}) vide")
        }
        if (isSelected) append(", sélectionné")
        if (isHighlightedMove) append(", déplacement possible")
        if (isCapturable) append(", pion adverse capturable")
    }

    val clickActionLabel = when {
        isCapturable -> "Capturer ce pion"
        isHighlightedMove -> "Déplacer le pion ici"
        isSelected -> "Désélectionner"
        piece != null -> "Sélectionner ce pion"
        else -> "Poser un pion ici"
    }

    Box(
        modifier = modifier
            .testTag("cell_${position.algebraic}")
            .padding(0.75.dp) // Ligne de grille fine
            .clip(RoundedCornerShape(3.dp))
            .background(cellBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = waliColors.accentGold),
                onClickLabel = clickActionLabel,
                onClick = onCellClicked
            )
            .semantics {
                contentDescription = accessibilityDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        // Numéro de case (1 à 30) en arrière-plan dans le coin supérieur gauche,
        // non cliquable, dégagé du pion central (bâtonnet ou caillou) et atténué si la case est occupée
        if (showCellNumber) {
            Text(
                text = cellNumberLabel,
                style = if (piece == null) emptyCellNumberStyle else occupiedCellNumberStyle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 3.dp, top = 1.5.dp)
                    .testTag("cell_number_${position.algebraic}")
            )
        }

        // Petit point d'ancrage central discret (style plateau traditionnel gravé)
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(waliColors.gridLine.copy(alpha = 0.25f))
        )

        // Indicateur discret de la case de départ du dernier coup
        if (isLastMoveOrigin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(waliColors.accentGold.copy(alpha = 0.12f))
                    .border(1.5.dp, waliColors.accentGold.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
            )
        }

        // Indicateur discret de la case d'arrivée du dernier coup
        if (isLastMoveDestination && !isPieceCurrentlyMovingToThis) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(waliColors.accentGold.copy(alpha = 0.16f))
                    .border(2.dp, waliColors.accentGold.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            )
        }

        // Indicateur de case accessible (déplacement valide)
        if (isHighlightedMove) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(waliColors.highlightMove.copy(alpha = 0.25f))
                    .border(2.dp, waliColors.highlightMove, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(waliColors.highlightMove)
                )
            }
        }

        // Pion présent sur la case avec animation d'apparition/disparition
        AnimatedVisibility(
            visible = piece != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.graphicsLayer {
                // Si le pion est actuellement en cours de vol vers cette case via l'overlay animé,
                // masquer le pion statique sous-jacent pour une transition visuelle parfaite
                alpha = if (isPieceCurrentlyMovingToThis) 0f else 1f
            }
        ) {
            piece?.let {
                PieceView(
                    player = it,
                    isSelected = isSelected,
                    isCapturable = isCapturable
                )
            }
        }

        // Surbrillance pion sélectionné
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(2.5.dp, waliColors.accentGold, RoundedCornerShape(6.dp))
            )
        }

        // Surbrillance cible capturable (pulsation vive)
        if (isCapturable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(3.dp, waliColors.captureAccent.copy(alpha = pulseAlpha), RoundedCornerShape(6.dp))
                    .background(waliColors.captureAccent.copy(alpha = pulseAlpha * 0.22f))
            )
        }
    }
}


