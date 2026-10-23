package com.example.wali.online

import com.example.wali.model.GameState
import com.example.wali.model.Player
import kotlinx.coroutines.flow.Flow

/**
 * Interface pour la gestion et la synchronisation des parties multijoueur à distance.
 * Permet l'injection de dépendances et le mock complet dans les tests unitaires.
 */
interface WaliOnlineRepository {

    /**
     * Identifiant unique Firebase Auth du joueur local (ou identifiant de test).
     */
    val currentUserId: String

    /**
     * S'assure que le joueur est authentifié (anonymement) et retourne son identifiant UID.
     */
    suspend fun ensureAuthenticated(): String

    /**
     * Crée une nouvelle partie avec le code spécifié, la visibilité et le camp du créateur.
     */
    suspend fun createGame(
        code: String,
        isPublic: Boolean,
        creatorSide: Player,
        initialGameState: GameState
    ): Result<OnlineGameSession>

    /**
     * Rejoint une partie existante en attente avec son code.
     * Attribue automatiquement le camp restant au joueur qui rejoint et passe le statut à "ongoing".
     */
    suspend fun joinGame(code: String): Result<OnlineGameSession>

    /**
     * Rejoint une partie existante en tant que spectateur (lecture seule du plateau, accès au chat)
     * à partir de son `spectatorCode` (7 caractères commençant par 'S').
     * Ne modifie ni `status`, ni `opponentUid`, ni `opponentPlayer`, ni `players`, ni `statsByUid`.
     */
    suspend fun joinAsSpectator(spectatorCode: String): Result<OnlineGameSession> =
        Result.failure(UnsupportedOperationException("Mode spectateur non pris en charge par ce repository."))

    /**
     * Recherche un salon à partir de son `spectatorCode`.
     */
    suspend fun findGameBySpectatorCode(spectatorCode: String): OnlineGameSession? = null

    /**
     * Écoute en temps réel les changements d'état du document d'une partie.
     */
    fun observeGame(code: String): Flow<OnlineGameSession?>

    /**
     * Écoute en temps réel la liste des parties publiques en attente d'un adversaire.
     */
    fun observePublicWaitingGames(): Flow<List<PublicGameItem>>

    /**
     * Met à jour l'état de jeu sur Firestore suite à un coup légalement validé localement.
     */
    suspend fun updateGameState(
        code: String,
        newGameState: GameState
    ): Result<Unit>

    /**
     * Déclare la victoire par abandon lorsque l'adversaire s'est déconnecté ou n'a plus joué.
     */
    suspend fun declareAbandonment(
        code: String,
        winnerPlayer: Player
    ): Result<Unit>

    /**
     * Vérifie si le joueur local a une partie "ongoing" en cours côté Firestore (pour dialogue de reprise).
     */
    suspend fun checkExistingOngoingGame(): OnlineGameSession?

    /**
     * Quitte ou annule une partie (ex: le créateur quitte pendant l'attente).
     */
    suspend fun leaveGame(code: String): Result<Unit>

    /**
     * Demande ou accepte une revanche dans le même salon [code].
     * Si les deux participants ont demandé la revanche, réinitialise la partie dans la même transaction.
     */
    suspend fun requestRematch(code: String): Result<Unit> = Result.success(Unit)

    /**
     * Annule sa propre demande de revanche en attente dans le salon [code].
     */
    suspend fun cancelRematchRequest(code: String): Result<Unit> = Result.success(Unit)

    /**
     * Propose un match nul pendant le tour du joueur [uid] en phase de déplacement.
     */
    suspend fun proposeDraw(code: String, uid: String = currentUserId): Result<Unit> = Result.success(Unit)

    /**
     * Répond à une proposition de match nul en attente ([accept] = true pour accepter, false pour refuser).
     */
    suspend fun respondToDraw(code: String, accept: Boolean, uid: String = currentUserId): Result<Unit> = Result.success(Unit)
}
