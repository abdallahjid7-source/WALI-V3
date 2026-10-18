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
 * Modèle complet représentant une session de partie en ligne synchronisée via Firestore.
 *
 * Structure d'un document de la collection "games" :
 * - code : code court alphanumérique à 6 caractères
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
    val abandonedBy: String? = null
) {
    /**
     * Retourne le camp (Player) attribué à un UID donné dans cette session.
     */
    fun getPlayerRole(uid: String): Player? {
        val roleStr = players[uid] ?: if (uid == creatorUid) creatorPlayer.name else if (uid == opponentUid) opponentPlayer?.name else null
        return roleStr?.let { runCatching { Player.valueOf(it) }.getOrNull() }
    }

    /**
     * Vrai si l'utilisateur avec cet UID participe à cette partie.
     */
    fun isParticipant(uid: String): Boolean {
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
 */
object WaliOnlineCodeGenerator {

    private const val CODE_LENGTH = 6
    private const val ALLOWED_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /**
     * Génère un code aléatoire de 6 caractères alphanumériques lisibles.
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
     * Vérifie si un code saisi respecte le format attendu (exactement 6 caractères alphanumériques).
     */
    fun isValidCode(code: String): Boolean {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != CODE_LENGTH) return false
        return trimmed.all { it.isLetterOrDigit() }
    }

    /**
     * Normalise un code saisi par l'utilisateur (suppression des espaces, mise en majuscules).
     */
    fun normalizeCode(code: String): String {
        return code.trim().uppercase()
    }
}
