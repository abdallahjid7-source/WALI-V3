package com.example.wali.online

import com.example.wali.model.GameState
import com.example.wali.model.Player
import kotlin.random.Random

/**
 * Statuts d'une partie en ligne Firestore.
 */
enum class OnlineGameStatus(val value: String) {
    WAITING("waiting"),
    ONGOING("ongoing"),
    FINISHED("finished"),
    ABANDONED("abandoned");

    companion object {
        fun fromValue(value: String?): OnlineGameStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: WAITING
        }
    }
}

/**
 * Rôle d'un utilisateur connecté à un salon en ligne.
 * Source unique de vérité pour distinguer les deux joueurs actifs d'un spectateur.
 */
enum class OnlineRole {
    CREATOR,
    OPPONENT,
    SPECTATOR
}

/**
 * Modèle complet représentant une session de partie en ligne synchronisée via Firestore.
 *
 * Structure d'un document de la collection "games" :
 * - code : code court alphanumérique à 6 caractères (pour l'adversaire)
 * - spectatorCode : code court à 7 caractères commençant par 'S' (pour les spectateurs)
 * - isPublic : visibilité dans la liste publique
 * - status : "waiting" / "ongoing" / "finished" / "abandoned"
 * - players : Map<uid, role> ("STICKS" ou "STONES")
 * - creatorUid : identifiant du créateur
 * - creatorPlayer : camp du créateur ("STICKS" ou "STONES")
 * - opponentUid : identifiant du joueur qui a rejoint (null tant qu'en attente)
 * - opponentPlayer : camp restant attribué au second joueur
 * - gameStateJson : chaîne JSON sérialisée avec WaliGameSerializer
 * - lastMoveAt : timestamp Unix (millisecondes) de la dernière action
 * - createdAt : timestamp Unix (millisecondes) de création du document
 * - winner : camp victorieux éventuel ("STICKS", "STONES" ou null)
 * - abandonedBy : uid du joueur ayant abandonné ou s'étant déconnecté
 */
data class OnlineGameSession(
    val code: String,
    val spectatorCode: String? = null,
    val isPublic: Boolean,
    val status: OnlineGameStatus,
    val creatorUid: String,
    val creatorPlayer: Player,
    val opponentUid: String? = null,
    val opponentPlayer: Player? = null,
    val players: Map<String, String> = emptyMap(),
    val gameState: GameState,
    val lastMoveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val winner: Player? = null,
    val abandonedBy: String? = null,
    val rematchRequestedBy: List<String> = emptyList(),
    val gamesPlayed: Int = 1,
    val statsByUid: Map<String, Int> = emptyMap(),
    /**
     * Marqueur explicite d'idempotence du comptage des victoires du salon :
     * - lastScoredGameIndex == gamesPlayed  ->  la manche en cours a déjà été comptabilisée
     * - lastScoredGameIndex <  gamesPlayed  ->  la manche en cours n'a pas encore été comptabilisée
     */
    val lastScoredGameIndex: Int = 0,
    /**
     * UID du joueur qui propose actuellement un match nul (null si aucune proposition en attente).
     */
    val drawOfferedBy: String? = null,
    /**
     * Numéro du tour (moveHistory.size) au moment de la dernière proposition de match nul,
     * permettant d'imposer l'attente d'au moins un tour complet avant de pouvoir reproposer après un refus.
     */
    val drawOfferTurnCount: Int = -1
) {
    /**
     * Retourne le rôle en ligne ([OnlineRole]) de [uid] dans cette session.
     */
    fun getOnlineRole(uid: String?): OnlineRole? {
        if (uid.isNullOrBlank()) return null
        return when {
            uid == creatorUid -> OnlineRole.CREATOR
            uid == opponentUid -> OnlineRole.OPPONENT
            else -> OnlineRole.SPECTATOR
        }
    }

    /**
     * Retourne le camp (Player) attribué à un UID donné dans cette session.
     * Retourne strictement null pour un spectateur.
     */
    fun getPlayerRole(uid: String): Player? {
        if (uid.isBlank()) return null
        val fromMap = players[uid]?.let { runCatching { Player.valueOf(it) }.getOrNull() }
        if (fromMap != null) return fromMap
        return when (uid) {
            creatorUid -> creatorPlayer
            opponentUid -> opponentPlayer ?: creatorPlayer.opponent
            else -> null
        }
    }

    /**
     * Résout l'UID du participant (uniquement parmi creatorUid et opponentUid) qui joue le camp [role].
     * Retourne null si le salon est incomplet ou si aucun des deux joueurs ne correspond.
     */
    fun resolveParticipantUidForRole(role: Player): String? {
        val oppUid = opponentUid?.takeIf { it.isNotBlank() } ?: return null
        if (creatorUid.isBlank()) return null
        val creatorRole = getPlayerRole(creatorUid) ?: creatorPlayer
        val opponentRole = getPlayerRole(oppUid) ?: opponentPlayer ?: creatorRole.opponent
        return when (role) {
            creatorRole -> creatorUid
            opponentRole -> oppUid
            else -> null
        }
    }

    /**
     * Retourne l'UID de l'adversaire par rapport à [uid].
     */
    fun getOpponentUid(uid: String): String? {
        return when (uid) {
            creatorUid -> opponentUid
            opponentUid -> creatorUid
            else -> opponentUid
        }
    }

    /**
     * Retourne le nombre de victoires cumulées dans ce salon pour [uid] (indexé par uid).
     */
    fun getWinsForUid(uid: String?): Int {
        if (uid.isNullOrBlank()) return 0
        return statsByUid[uid] ?: 0
    }

    /**
     * Vrai si l'utilisateur avec cet UID participe activement à cette partie (créateur ou adversaire).
     * Retourne false pour un spectateur.
     */
    fun isParticipant(uid: String): Boolean {
        if (uid.isBlank()) return false
        return uid == creatorUid || uid == opponentUid || players.containsKey(uid)
    }

    /**
     * Vrai si c'est actuellement le tour du joueur avec cet UID.
     */
    fun isPlayerTurn(uid: String): Boolean {
        val role = getPlayerRole(uid) ?: return false
        return gameState.currentPlayer == role && status == OnlineGameStatus.ONGOING
    }
}

/**
 * Sélectionne de manière strictement déterministe le salon cible lorsqu'une requête par
 * `spectatorCode` retourne un ou plusieurs documents (protection en lecture contre les collisions).
 *
 * Ordre de priorité :
 * 1. Salons actifs d'abord : ONGOING (priorité 3) > WAITING (priorité 2) > FINISHED (priorité 1) > ABANDONED (priorité 0)
 * 2. Activité la plus récente (`lastMoveAt` décroissant)
 * 3. Création la plus récente (`createdAt` décroissant)
 * 4. Ordre lexicographique du `code` (déterminisme total en cas d'égalité parfaite d'horodatage)
 */
fun selectDeterministicSpectatorSession(candidates: List<OnlineGameSession>): OnlineGameSession? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    fun statusRank(status: OnlineGameStatus): Int = when (status) {
        OnlineGameStatus.ONGOING -> 3
        OnlineGameStatus.WAITING -> 2
        OnlineGameStatus.FINISHED -> 1
        OnlineGameStatus.ABANDONED -> 0
    }

    return candidates.WithDeterministicOrder(::statusRank).firstOrNull()
}

private fun List<OnlineGameSession>.WithDeterministicOrder(
    statusRank: (OnlineGameStatus) -> Int
): List<OnlineGameSession> {
    return this.sortedWith(
        compareByDescending<OnlineGameSession> { statusRank(it.status) }
            .thenByDescending { it.lastMoveAt }
            .thenByDescending { it.createdAt }
            .thenBy { it.code }
    )
}

/**
 * Résumé d'une partie publique en attente affichée dans la liste du lobby.
 */
data class PublicGameItem(
    val code: String,
    val creatorSide: Player,
    val createdAt: Long,
    val mandatoryAlignment: Boolean = false,
    val defeatAtTwoPieces: Boolean = false
)

/**
 * Générateur et validateur de codes de partie uniques, courts et mémorisables.
 *
 * Utilise un jeu de caractères sans ambiguïté visuelle :
 * - Chiffres 2 à 9 (sans 0 ni 1)
 * - Lettres majuscules A à Z (sans O ni I)
 *
 * Format des codes :
 * - Code joueur : exactement 6 caractères alphanumériques (ex: "W7K9M2")
 * - Code spectateur : exactement 7 caractères commençant par 'S' suivi de 6 caractères (ex: "SW7K9M2")
 */
object WaliOnlineCodeGenerator {

    const val CODE_LENGTH = 6
    const val SPECTATOR_CODE_LENGTH = 7
    const val SPECTATOR_PREFIX = 'S'
    private const val ALLOWED_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /**
     * Génère un code joueur aléatoire de 6 caractères alphanumériques lisibles.
     */
    fun generateCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            val randomIndex = Random.nextInt(ALLOWED_CHARS.length)
            sb.append(ALLOWED_CHARS[randomIndex])
        }
        return sb.toString()
    }

    /**
     * Génère un code spectateur aléatoire de 7 caractères ('S' + 6 caractères alphanumériques lisibles).
     */
    fun generateSpectatorCode(): String {
        return "$SPECTATOR_PREFIX${generateCode()}"
    }

    /**
     * Vérifie si un code saisi respecte le format attendu d'un code joueur (exactement 6 caractères alphanumériques).
     */
    fun isValidCode(code: String): Boolean {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != CODE_LENGTH) return false
        return trimmed.all { it.isLetterOrDigit() }
    }

    /**
     * Vérifie si un code saisi respecte le format attendu d'un code spectateur
     * (exactement 7 caractères alphanumériques commençant par 'S').
     */
    fun isValidSpectatorCode(code: String): Boolean {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != SPECTATOR_CODE_LENGTH) return false
        if (trimmed.first() != SPECTATOR_PREFIX) return false
        return trimmed.drop(1).all { it.isLetterOrDigit() }
    }

    /**
     * Normalise un code saisi par l'utilisateur (suppression des espaces, mise en majuscules).
     */
    fun normalizeCode(code: String): String {
        return code.trim().uppercase()
    }
}
