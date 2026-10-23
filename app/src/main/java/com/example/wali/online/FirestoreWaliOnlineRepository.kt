package com.example.wali.online

import android.util.Log
import com.example.wali.model.GameState
import com.example.wali.model.Player
import com.example.wali.storage.WaliGameSerializer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implémentation de production de [WaliOnlineRepository] basée sur Cloud Firestore et Firebase Auth.
 */
class FirestoreWaliOnlineRepository(
    private val firestore: FirebaseFirestore = WaliFirebaseProvider.getFirestore(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : WaliOnlineRepository {

    companion object {
        private const val TAG = "FirestoreWaliOnline"
        const val COLLECTION_GAMES = "games"

        const val FIELD_CODE = "code"
        const val FIELD_SPECTATOR_CODE = "spectatorCode"
        const val FIELD_IS_PUBLIC = "isPublic"
        const val FIELD_STATUS = "status"
        const val FIELD_CREATOR_UID = "creatorUid"
        const val FIELD_CREATOR_PLAYER = "creatorPlayer"
        const val FIELD_OPPONENT_UID = "opponentUid"
        const val FIELD_OPPONENT_PLAYER = "opponentPlayer"
        const val FIELD_PLAYERS = "players"
        const val FIELD_GAME_STATE_JSON = "gameStateJson"
        const val FIELD_LAST_MOVE_AT = "lastMoveAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_WINNER = "winner"
        const val FIELD_ABANDONED_BY = "abandonedBy"
        const val FIELD_MANDATORY_ALIGNMENT = "mandatoryAlignment"
        const val FIELD_DEFEAT_AT_TWO_PIECES = "defeatAtTwoPieces"
        const val FIELD_REMATCH_REQUESTED_BY = "rematchRequestedBy"
        const val FIELD_GAMES_PLAYED = "gamesPlayed"
        const val FIELD_STATS_BY_UID = "statsByUid"
        const val FIELD_LAST_SCORED_GAME_INDEX = "lastScoredGameIndex"
        const val FIELD_DRAW_OFFERED_BY = "drawOfferedBy"
        const val FIELD_DRAW_OFFER_TURN_COUNT = "drawOfferTurnCount"
        private const val MAX_SPECTATOR_CODE_ATTEMPTS = 5

        @Volatile
        private var fallbackAnonymousUid: String? = null

        private fun getOrCreateFallbackUid(): String {
            return fallbackAnonymousUid ?: synchronized(this) {
                fallbackAnonymousUid ?: ("anon_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)).also {
                    fallbackAnonymousUid = it
                }
            }
        }
    }

    override val currentUserId: String
        get() = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: getOrCreateFallbackUid()

    override suspend fun ensureAuthenticated(): String {
        val existing = auth.currentUser?.uid
        if (!existing.isNullOrBlank()) {
            return existing
        }
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid?.takeIf { it.isNotBlank() } ?: getOrCreateFallbackUid()
        } catch (e: Exception) {
            Log.d(TAG, "Authentification anonyme locale de secours utilisée : ${e.message}")
            getOrCreateFallbackUid()
        }
    }

    override suspend fun createGame(
        code: String,
        isPublic: Boolean,
        creatorSide: Player,
        initialGameState: GameState
    ): Result<OnlineGameSession> {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            runCatching { firestore.enableNetwork().await() }

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val existing = runCatching { docRef.get().await() }.getOrNull()
            if (existing != null && existing.exists()) {
                throw IllegalStateException("Un salon avec ce code existe déjà.")
            }

            // Génération d'un spectatorCode de 7 caractères ('S' + 6 caractères) avec vérification
            // d'unicité globale sur toute la collection "games" (requête simple à 1 champ, zéro index composite requis).
            var spectatorCode = WaliOnlineCodeGenerator.generateSpectatorCode()
            for (attempt in 1..MAX_SPECTATOR_CODE_ATTEMPTS) {
                val candidate = WaliOnlineCodeGenerator.generateSpectatorCode()
                val duplicateSnap = runCatching {
                    firestore.collection(COLLECTION_GAMES)
                        .whereEqualTo(FIELD_SPECTATOR_CODE, candidate)
                        .limit(1)
                        .get()
                        .await()
                }.getOrNull()
                if (duplicateSnap == null || duplicateSnap.isEmpty) {
                    spectatorCode = candidate
                    break
                }
            }

            val now = System.currentTimeMillis()
            val stateJson = WaliGameSerializer.serializeGameState(initialGameState)
            val playersMap = mapOf(uid to creatorSide.name)
            val initialStats = mapOf(uid to 0)

            val data = hashMapOf<String, Any?>(
                FIELD_CODE to code,
                FIELD_SPECTATOR_CODE to spectatorCode,
                FIELD_IS_PUBLIC to isPublic,
                FIELD_STATUS to OnlineGameStatus.WAITING.value,
                FIELD_CREATOR_UID to uid,
                FIELD_CREATOR_PLAYER to creatorSide.name,
                FIELD_OPPONENT_UID to null,
                FIELD_OPPONENT_PLAYER to null,
                FIELD_PLAYERS to playersMap,
                FIELD_GAME_STATE_JSON to stateJson,
                FIELD_LAST_MOVE_AT to now,
                FIELD_CREATED_AT to now,
                FIELD_WINNER to null,
                FIELD_ABANDONED_BY to null,
                FIELD_MANDATORY_ALIGNMENT to initialGameState.ruleOptions.mandatoryAlignment,
                FIELD_DEFEAT_AT_TWO_PIECES to initialGameState.ruleOptions.defeatAtTwoPieces,
                FIELD_REMATCH_REQUESTED_BY to emptyList<String>(),
                FIELD_GAMES_PLAYED to 1,
                FIELD_STATS_BY_UID to initialStats,
                FIELD_LAST_SCORED_GAME_INDEX to 0,
                FIELD_DRAW_OFFERED_BY to null,
                FIELD_DRAW_OFFER_TURN_COUNT to -1
            )

            docRef.set(data).await()

            OnlineGameSession(
                code = code,
                spectatorCode = spectatorCode,
                isPublic = isPublic,
                status = OnlineGameStatus.WAITING,
                creatorUid = uid,
                creatorPlayer = creatorSide,
                opponentUid = null,
                opponentPlayer = null,
                players = playersMap,
                gameState = initialGameState,
                lastMoveAt = now,
                createdAt = now,
                winner = null,
                abandonedBy = null,
                rematchRequestedBy = emptyList(),
                gamesPlayed = 1,
                statsByUid = initialStats,
                lastScoredGameIndex = 0,
                drawOfferedBy = null,
                drawOfferTurnCount = -1
            )
        }
    }

    override suspend fun joinGame(code: String): Result<OnlineGameSession> {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("Code de partie introuvable.")
                }

                val status = snapshot.getString(FIELD_STATUS)
                val creatorUid = snapshot.getString(FIELD_CREATOR_UID) ?: ""
                val creatorPlayerStr = snapshot.getString(FIELD_CREATOR_PLAYER) ?: Player.STICKS.name
                val creatorPlayer = runCatching { Player.valueOf(creatorPlayerStr) }.getOrDefault(Player.STICKS)

                if (creatorUid == uid) {
                    // Le créateur rejoint son propre salon en attente
                    val session = parseSession(snapshot) ?: throw IllegalStateException("Format de partie invalide.")
                    return@runTransaction session
                }

                if (status != OnlineGameStatus.WAITING.value) {
                    throw IllegalStateException("Cette partie n'est plus en attente d'un joueur.")
                }

                val opponentPlayer = creatorPlayer.opponent
                val now = System.currentTimeMillis()
                val updatedPlayers = mapOf(
                    creatorUid to creatorPlayer.name,
                    uid to opponentPlayer.name
                )
                val existingStats = parseStatsMap(snapshot.get(FIELD_STATS_BY_UID))
                val updatedStats = existingStats.toMutableMap().apply {
                    putIfAbsent(creatorUid, 0)
                    putIfAbsent(uid, 0)
                }

                transaction.update(
                    docRef,
                    mapOf(
                        FIELD_STATUS to OnlineGameStatus.ONGOING.value,
                        FIELD_OPPONENT_UID to uid,
                        FIELD_OPPONENT_PLAYER to opponentPlayer.name,
                        FIELD_PLAYERS to updatedPlayers,
                        FIELD_STATS_BY_UID to updatedStats,
                        FIELD_LAST_MOVE_AT to now
                    )
                )

                null
            }.await()

            // Récupère la session à jour
            val updatedSnap = docRef.get().await()
            parseSession(updatedSnap) ?: throw IllegalStateException("Impossible de lire la partie mise à jour.")
        }
    }

    override suspend fun joinAsSpectator(spectatorCode: String): Result<OnlineGameSession> {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            val normalized = WaliOnlineCodeGenerator.normalizeCode(spectatorCode)
            if (!WaliOnlineCodeGenerator.isValidSpectatorCode(normalized)) {
                throw IllegalArgumentException("Code spectateur invalide.")
            }

            val session = findGameBySpectatorCode(normalized)
                ?: throw IllegalArgumentException("Aucun salon trouvé pour ce code spectateur.")

            session
        }
    }

    override suspend fun findGameBySpectatorCode(spectatorCode: String): OnlineGameSession? {
        return runCatching {
            val normalized = WaliOnlineCodeGenerator.normalizeCode(spectatorCode)
            if (!WaliOnlineCodeGenerator.isValidSpectatorCode(normalized)) return null

            val snapshot = firestore.collection(COLLECTION_GAMES)
                .whereEqualTo(FIELD_SPECTATOR_CODE, normalized)
                .get()
                .await()

            if (snapshot.isEmpty) return null

            val candidates = snapshot.documents.mapNotNull { doc -> parseSession(doc) }
            selectDeterministicSpectatorSession(candidates)
        }.getOrNull()
    }

    override fun observeGame(code: String): Flow<OnlineGameSession?> = callbackFlow {
        val docRef = firestore.collection(COLLECTION_GAMES).document(code)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Erreur écoute partie $code : ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val session = parseSession(snapshot)
                trySend(session)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observePublicWaitingGames(): Flow<List<PublicGameItem>> = callbackFlow {
        val query = firestore.collection(COLLECTION_GAMES)
            .whereEqualTo(FIELD_STATUS, OnlineGameStatus.WAITING.value)
            .whereEqualTo(FIELD_IS_PUBLIC, true)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Erreur écoute parties publiques : ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val code = doc.getString(FIELD_CODE) ?: doc.id
                    val sideStr = doc.getString(FIELD_CREATOR_PLAYER) ?: Player.STICKS.name
                    val side = runCatching { Player.valueOf(sideStr) }.getOrDefault(Player.STICKS)
                    val createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L
                    val mandatoryAlignment = doc.getBoolean(FIELD_MANDATORY_ALIGNMENT) ?: false
                    val defeatAtTwoPieces = doc.getBoolean(FIELD_DEFEAT_AT_TWO_PIECES) ?: false
                    PublicGameItem(
                        code = code,
                        creatorSide = side,
                        createdAt = createdAt,
                        mandatoryAlignment = mandatoryAlignment,
                        defeatAtTwoPieces = defeatAtTwoPieces
                    )
                }.sortedByDescending { it.createdAt }

                trySend(list)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun updateGameState(
        code: String,
        newGameState: GameState
    ): Result<Unit> {
        return runCatching {
            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val now = System.currentTimeMillis()
            val stateJson = WaliGameSerializer.serializeGameState(newGameState)
            val isGameEnding = newGameState.winner != null || newGameState.phase == com.example.wali.model.Phase.GAME_OVER
            val myUid = currentUserId

            if (!isGameEnding) {
                val updates = mapOf<String, Any?>(
                    FIELD_GAME_STATE_JSON to stateJson,
                    FIELD_LAST_MOVE_AT to now
                )
                docRef.update(updates).await()
            } else {
                // Fin de manche : transaction atomique et idempotente pour comptabiliser la victoire dès la fin de partie.
                // Règle de course : le premier écrit gagne. Si status != ONGOING, on abandonne sans réécrire ni lever d'erreur.
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    if (!snapshot.exists()) return@runTransaction null
                    val session = parseSession(snapshot) ?: return@runTransaction null

                    // Garde spectateur : seuls les deux joueurs participants peuvent muter le salon
                    if (!session.isParticipant(myUid)) {
                        return@runTransaction null
                    }

                    if (session.status != OnlineGameStatus.ONGOING) {
                        return@runTransaction null
                    }

                    val (updatedStats, newLastScoredIndex) = computeScoredSalonState(
                        session = session.copy(gameState = newGameState),
                        winnerRole = newGameState.winner
                    )

                    val updates = mutableMapOf<String, Any?>(
                        FIELD_GAME_STATE_JSON to stateJson,
                        FIELD_LAST_MOVE_AT to now,
                        FIELD_STATUS to OnlineGameStatus.FINISHED.value,
                        FIELD_WINNER to newGameState.winner?.name,
                        FIELD_DRAW_OFFERED_BY to null,
                        FIELD_STATS_BY_UID to updatedStats,
                        FIELD_LAST_SCORED_GAME_INDEX to newLastScoredIndex
                    )
                    transaction.update(docRef, updates)
                    null
                }.await()
            }
            Unit
        }
    }

    override suspend fun declareAbandonment(
        code: String,
        winnerPlayer: Player
    ): Result<Unit> {
        return runCatching {
            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val now = System.currentTimeMillis()
            val myUid = currentUserId

            // Fin de manche par forfait ou inactivité : transaction atomique et idempotente.
            // Règle de course : le premier écrit gagne. Si status != ONGOING, on ne réécrit ni winner, ni abandonedBy, ni statsByUid.
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) return@runTransaction null
                val session = parseSession(snapshot) ?: return@runTransaction null

                // Garde spectateur : un spectateur ne peut jamais déclarer d'abandon
                if (!session.isParticipant(myUid)) {
                    return@runTransaction null
                }

                if (session.status != OnlineGameStatus.ONGOING) {
                    return@runTransaction null
                }

                // Point 1 & Vérification 1 : abandonedBy désigne toujours le joueur qui a abandonné
                // (camp perdant = winnerPlayer.opponent). Aucun repli sur currentUserId : si le salon
                // est incomplet, abandonedBy reste honnêtement null.
                val abandonedByUid: String? = session.resolveParticipantUidForRole(winnerPlayer.opponent)

                val (updatedStats, newLastScoredIndex) = computeScoredSalonState(
                    session = session,
                    winnerRole = winnerPlayer
                )

                val updates = mapOf<String, Any?>(
                    FIELD_STATUS to OnlineGameStatus.ABANDONED.value,
                    FIELD_WINNER to winnerPlayer.name,
                    FIELD_ABANDONED_BY to abandonedByUid,
                    FIELD_DRAW_OFFERED_BY to null,
                    FIELD_STATS_BY_UID to updatedStats,
                    FIELD_LAST_SCORED_GAME_INDEX to newLastScoredIndex,
                    FIELD_LAST_MOVE_AT to now
                )
                transaction.update(docRef, updates)
                null
            }.await()
            Unit
        }
    }

    override suspend fun proposeDraw(code: String, uid: String): Result<Unit> {
        return runCatching {
            val actorUid = uid.ifBlank { ensureAuthenticated() }
            if (actorUid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val now = System.currentTimeMillis()

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("Salon introuvable.")
                }
                val session = parseSession(snapshot)
                    ?: throw IllegalStateException("Format de salon invalide.")

                if (!session.isParticipant(actorUid)) {
                    throw IllegalStateException("Seul un joueur participant peut proposer un match nul.")
                }
                if (session.status != OnlineGameStatus.ONGOING) {
                    throw IllegalStateException("La partie n'est pas en cours.")
                }
                if (session.gameState.phase != com.example.wali.model.Phase.MOVEMENT || session.gameState.winner != null) {
                    throw IllegalStateException("Le match nul ne peut être proposé qu'en phase de déplacement.")
                }
                if (!session.isPlayerTurn(actorUid)) {
                    throw IllegalStateException("Vous ne pouvez proposer un match nul que pendant votre tour.")
                }
                if (session.drawOfferedBy != null) {
                    throw IllegalStateException("Une proposition de match nul est déjà en attente.")
                }
                val currentTurnCount = session.gameState.moveHistory.size
                if (session.drawOfferTurnCount >= 0 && currentTurnCount <= session.drawOfferTurnCount) {
                    throw IllegalStateException("Vous devez attendre au moins un tour complet avant de reproposer un match nul.")
                }

                transaction.update(
                    docRef,
                    mapOf(
                        FIELD_DRAW_OFFERED_BY to actorUid,
                        FIELD_DRAW_OFFER_TURN_COUNT to currentTurnCount,
                        FIELD_LAST_MOVE_AT to now
                    )
                )
                null
            }.await()
            Unit
        }
    }

    override suspend fun respondToDraw(code: String, accept: Boolean, uid: String): Result<Unit> {
        return runCatching {
            val actorUid = uid.ifBlank { ensureAuthenticated() }
            if (actorUid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val now = System.currentTimeMillis()

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("Salon introuvable.")
                }
                val session = parseSession(snapshot)
                    ?: throw IllegalStateException("Format de salon invalide.")

                if (!session.isParticipant(actorUid)) {
                    throw IllegalStateException("Seul un joueur participant peut répondre au match nul.")
                }
                if (session.status != OnlineGameStatus.ONGOING) {
                    throw IllegalStateException("La partie n'est pas en cours.")
                }
                val offeredBy = session.drawOfferedBy
                    ?: throw IllegalStateException("Aucune proposition de match nul en attente.")
                if (offeredBy == actorUid) {
                    throw IllegalStateException("Seul l'adversaire peut répondre à la proposition de match nul.")
                }

                if (accept) {
                    val endedState = session.gameState.copy(
                        phase = com.example.wali.model.Phase.GAME_OVER,
                        winner = null,
                        pendingCaptures = 0,
                        moveHistory = session.gameState.moveHistory + "Match nul — Les deux joueurs ont accepté."
                    )
                    val stateJson = WaliGameSerializer.serializeGameState(endedState)
                    val (updatedStats, newLastScoredIndex) = computeScoredSalonState(
                        session = session.copy(gameState = endedState),
                        winnerRole = null
                    )

                    transaction.update(
                        docRef,
                        mapOf(
                            FIELD_GAME_STATE_JSON to stateJson,
                            FIELD_STATUS to OnlineGameStatus.FINISHED.value,
                            FIELD_WINNER to null,
                            FIELD_DRAW_OFFERED_BY to null,
                            FIELD_STATS_BY_UID to updatedStats,
                            FIELD_LAST_SCORED_GAME_INDEX to newLastScoredIndex,
                            FIELD_LAST_MOVE_AT to now
                        )
                    )
                } else {
                    val refuserRole = session.getPlayerRole(actorUid)
                    val baseState = session.gameState.copy(pendingCaptures = 0)
                    val nextState = if (refuserRole != null && baseState.currentPlayer != refuserRole) {
                        com.example.wali.engine.WaliEngine.switchPlayer(baseState)
                    } else {
                        baseState
                    }
                    val stateJson = WaliGameSerializer.serializeGameState(nextState)

                    transaction.update(
                        docRef,
                        mapOf(
                            FIELD_GAME_STATE_JSON to stateJson,
                            FIELD_DRAW_OFFERED_BY to null,
                            FIELD_LAST_MOVE_AT to now
                        )
                    )
                }
                null
            }.await()
            Unit
        }
    }

    override suspend fun checkExistingOngoingGame(): OnlineGameSession? {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) return null

            // Recherche si le joueur est créateur d'une partie "ongoing"
            val queryCreator = firestore.collection(COLLECTION_GAMES)
                .whereEqualTo(FIELD_STATUS, OnlineGameStatus.ONGOING.value)
                .whereEqualTo(FIELD_CREATOR_UID, uid)
                .limit(1)
                .get()
                .await()

            if (!queryCreator.isEmpty) {
                return parseSession(queryCreator.documents.first())
            }

            // Recherche si le joueur est l'adversaire d'une partie "ongoing"
            val queryOpponent = firestore.collection(COLLECTION_GAMES)
                .whereEqualTo(FIELD_STATUS, OnlineGameStatus.ONGOING.value)
                .whereEqualTo(FIELD_OPPONENT_UID, uid)
                .limit(1)
                .get()
                .await()

            if (!queryOpponent.isEmpty) {
                return parseSession(queryOpponent.documents.first())
            }

            null
        }.getOrNull()
    }

    override suspend fun leaveGame(code: String): Result<Unit> {
        return runCatching {
            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return Result.success(Unit)

            val myUid = currentUserId
            val creatorUid = snapshot.getString(FIELD_CREATOR_UID) ?: ""
            val opponentUid = snapshot.getString(FIELD_OPPONENT_UID)

            // Garde spectateur : quitter le salon pour un spectateur ne doit ni supprimer le salon en attente
            // ni écrire LEFT:<uid> dans abandonedBy.
            if (myUid != creatorUid && myUid != opponentUid) {
                return Result.success(Unit)
            }

            val status = snapshot.getString(FIELD_STATUS)
            if (status == OnlineGameStatus.WAITING.value && myUid == creatorUid) {
                // Si la partie était en attente et que c'est le créateur qui quitte, la supprimer pour nettoyer le lobby
                docRef.delete().await()
            } else if (status == OnlineGameStatus.FINISHED.value || status == OnlineGameStatus.ABANDONED.value) {
                // Signale à l'autre joueur en attente de revanche que ce joueur a quitté le salon
                docRef.update(
                    mapOf(
                        FIELD_ABANDONED_BY to "LEFT:$myUid",
                        FIELD_REMATCH_REQUESTED_BY to emptyList<String>(),
                        FIELD_LAST_MOVE_AT to System.currentTimeMillis()
                    )
                ).await()
            }
            Unit
        }
    }

    override suspend fun requestRematch(code: String): Result<Unit> {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) {
                throw IllegalStateException("Authentification Firebase requise.")
            }

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("L'adversaire a quitté le salon")
                }

                val session = parseSession(snapshot)
                    ?: throw IllegalStateException("Format de salon invalide.")

                val creatorUid = session.creatorUid
                val opponentUid = session.opponentUid
                if (opponentUid.isNullOrBlank() || session.abandonedBy?.startsWith("LEFT:") == true) {
                    throw IllegalStateException("L'adversaire a quitté le salon")
                }
                if (uid != creatorUid && uid != opponentUid) {
                    throw IllegalStateException("Vous ne participez pas à ce salon.")
                }

                // Si la revanche a déjà été réinitialisée par une acceptation quasi simultanée, ne rien refaire
                if (session.status == OnlineGameStatus.ONGOING) {
                    return@runTransaction null
                }

                val updatedRequests = (session.rematchRequestedBy + uid).distinct()
                val bothAccepted = updatedRequests.contains(creatorUid) && updatedRequests.contains(opponentUid)
                val now = System.currentTimeMillis()

                if (!bothAccepted) {
                    transaction.update(
                        docRef,
                        mapOf(
                            FIELD_REMATCH_REQUESTED_BY to updatedRequests,
                            FIELD_LAST_MOVE_AT to now
                        )
                    )
                } else {
                    // Les deux joueurs ont accepté : réinitialisation atomique dans la MÊME transaction.
                    // Résolution du camp vainqueur depuis le champ winner déjà stocké dans le document :
                    val storedWinnerRole: Player? = session.winner ?: session.gameState.winner

                    // Filet de sécurité de compatibilité ascendante uniquement :
                    // En fonctionnement normal (Partie D), la manche a déjà été comptabilisée à sa fin
                    // (lastScoredGameIndex == gamesPlayed) et computeScoredSalonState est alors un no-op.
                    // Ce rattrapage ne s'exécute que si un document antérieur est arrivé en état FINISHED/ABANDONED
                    // avec lastScoredGameIndex < gamesPlayed.
                    val (updatedStats, scoredIndex) = computeScoredSalonState(
                        session = session,
                        winnerRole = storedWinnerRole
                    )

                    val winnerUid: String = (storedWinnerRole?.let { session.resolveParticipantUidForRole(it) })
                        ?: session.abandonedBy?.takeIf { !it.startsWith("LEFT:") }?.let { abUid ->
                            if (abUid == creatorUid) opponentUid else creatorUid
                        }
                        ?: creatorUid

                    // Le vainqueur de la partie précédente reçoit STICKS (commence toujours), le perdant reçoit STONES
                    val newCreatorPlayer = if (winnerUid == creatorUid) Player.STICKS else Player.STONES
                    val newOpponentPlayer = newCreatorPlayer.opponent
                    val newPlayersMap = mapOf(
                        creatorUid to newCreatorPlayer.name,
                        opponentUid to newOpponentPlayer.name
                    )

                    val newGamesPlayed = session.gamesPlayed + 1

                    // Réinitialisation complète avec les variantes d'origine du salon
                    val preservedRuleOptions = session.gameState.ruleOptions
                    val newGameState = com.example.wali.engine.WaliEngine.initializeGame(ruleOptions = preservedRuleOptions)
                    val newStateJson = WaliGameSerializer.serializeGameState(newGameState)

                    transaction.update(
                        docRef,
                        mapOf(
                            FIELD_STATUS to OnlineGameStatus.ONGOING.value,
                            FIELD_CREATOR_PLAYER to newCreatorPlayer.name,
                            FIELD_OPPONENT_PLAYER to newOpponentPlayer.name,
                            FIELD_PLAYERS to newPlayersMap,
                            FIELD_GAME_STATE_JSON to newStateJson,
                            FIELD_WINNER to null,
                            FIELD_ABANDONED_BY to null,
                            FIELD_DRAW_OFFERED_BY to null,
                            FIELD_DRAW_OFFER_TURN_COUNT to -1,
                            FIELD_REMATCH_REQUESTED_BY to emptyList<String>(),
                            FIELD_GAMES_PLAYED to newGamesPlayed,
                            FIELD_STATS_BY_UID to updatedStats,
                            FIELD_LAST_SCORED_GAME_INDEX to scoredIndex,
                            FIELD_LAST_MOVE_AT to now
                        )
                    )
                }
                null
            }.await()
            Unit
        }
    }

    override suspend fun cancelRematchRequest(code: String): Result<Unit> {
        return runCatching {
            val uid = ensureAuthenticated()
            if (uid.isBlank()) return Result.success(Unit)

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) return@runTransaction null
                val currentList = parseStringList(snapshot.get(FIELD_REMATCH_REQUESTED_BY))
                val updatedList = currentList.filter { it != uid }
                transaction.update(
                    docRef,
                    mapOf(
                        FIELD_REMATCH_REQUESTED_BY to updatedList,
                        FIELD_LAST_MOVE_AT to System.currentTimeMillis()
                    )
                )
                null
            }.await()
            Unit
        }
    }

    private fun parseStringList(raw: Any?): List<String> {
        if (raw is List<*>) {
            return raw.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        }
        return emptyList()
    }

    private fun parseStatsMap(raw: Any?): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        if (raw is Map<*, *>) {
            raw.forEach { (k, v) ->
                val key = k?.toString()
                val value = when (v) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull()
                    else -> null
                }
                if (!key.isNullOrBlank() && value != null) {
                    result[key] = value
                }
            }
        }
        return result
    }

    private fun parseSession(doc: DocumentSnapshot): OnlineGameSession? {
        val code = doc.getString(FIELD_CODE) ?: doc.id
        val spectatorCode = doc.getString(FIELD_SPECTATOR_CODE)
        val isPublic = doc.getBoolean(FIELD_IS_PUBLIC) ?: false
        val status = OnlineGameStatus.fromValue(doc.getString(FIELD_STATUS))
        val creatorUid = doc.getString(FIELD_CREATOR_UID) ?: return null
        val creatorPlayerStr = doc.getString(FIELD_CREATOR_PLAYER) ?: Player.STICKS.name
        val creatorPlayer = runCatching { Player.valueOf(creatorPlayerStr) }.getOrDefault(Player.STICKS)
        val opponentUid = doc.getString(FIELD_OPPONENT_UID)
        val opponentPlayerStr = doc.getString(FIELD_OPPONENT_PLAYER)
        val opponentPlayer = opponentPlayerStr?.let { runCatching { Player.valueOf(it) }.getOrNull() }

        val playersMap = mutableMapOf<String, String>()
        val rawPlayers = doc.get(FIELD_PLAYERS)
        if (rawPlayers is Map<*, *>) {
            rawPlayers.forEach { (k, v) ->
                if (k != null && v != null) {
                    playersMap[k.toString()] = v.toString()
                }
            }
        }

        val stateJson = doc.getString(FIELD_GAME_STATE_JSON)
        val gameState = WaliGameSerializer.deserializeGameState(stateJson) ?: return null

        val lastMoveAt = doc.getLong(FIELD_LAST_MOVE_AT) ?: 0L
        val createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L
        val winnerStr = doc.getString(FIELD_WINNER)
        val winner = winnerStr?.let { runCatching { Player.valueOf(it) }.getOrNull() }
        val abandonedBy = doc.getString(FIELD_ABANDONED_BY)
        val rematchRequestedBy = parseStringList(doc.get(FIELD_REMATCH_REQUESTED_BY))
        val gamesPlayed = (doc.getLong(FIELD_GAMES_PLAYED) ?: 1L).toInt().coerceAtLeast(1)
        val statsByUid = parseStatsMap(doc.get(FIELD_STATS_BY_UID))
        val lastScoredGameIndex = (doc.getLong(FIELD_LAST_SCORED_GAME_INDEX) ?: 0L).toInt().coerceAtLeast(0)
        val drawOfferedBy = doc.getString(FIELD_DRAW_OFFERED_BY)?.takeIf { it.isNotBlank() }
        val drawOfferTurnCount = (doc.getLong(FIELD_DRAW_OFFER_TURN_COUNT) ?: -1L).toInt()

        return OnlineGameSession(
            code = code,
            spectatorCode = spectatorCode,
            isPublic = isPublic,
            status = status,
            creatorUid = creatorUid,
            creatorPlayer = creatorPlayer,
            opponentUid = opponentUid,
            opponentPlayer = opponentPlayer,
            players = playersMap,
            gameState = gameState,
            lastMoveAt = lastMoveAt,
            createdAt = createdAt,
            winner = winner,
            abandonedBy = abandonedBy,
            rematchRequestedBy = rematchRequestedBy,
            gamesPlayed = gamesPlayed,
            statsByUid = statsByUid,
            lastScoredGameIndex = lastScoredGameIndex,
            drawOfferedBy = drawOfferedBy,
            drawOfferTurnCount = drawOfferTurnCount
        )
    }

    /**
     * Retourne le nombre de points attribués au vainqueur [winnerRole] pour la manche :
     * 2 points si le perdant a 0 capture (victoire écrasante), 1 point sinon.
     */
    private fun pointsForWin(session: OnlineGameSession, winnerRole: Player): Int {
        val loserRole = winnerRole.opponent
        return if (session.gameState.capturesMadeBy(loserRole) == 0) 2 else 1
    }

    /**
     * Calcule de manière strictement idempotente le score du salon ([statsByUid]) et le marqueur
     * [lastScoredGameIndex] à la fin d'une manche.
     *
     * Invariant du marqueur :
     * - lastScoredGameIndex == gamesPlayed  ->  la manche en cours a déjà été comptabilisée
     * - lastScoredGameIndex <  gamesPlayed  ->  la manche en cours n'a pas encore été comptabilisée
     *
     * Garde salon incomplet (Point 4) :
     * Si [OnlineGameSession.opponentUid] est nul ou vide, aucune écriture dans statsByUid n'est
     * effectuée et le marqueur [lastScoredGameIndex] n'avance pas.
     *
     * Exclusivité des participants (D.3) :
     * Seuls [OnlineGameSession.creatorUid] et [OnlineGameSession.opponentUid] peuvent figurer dans [statsByUid].
     *
     * Manche sans vainqueur (Point 3 / Match nul) :
     * Si [winnerRole] est null alors que le salon est complet, la manche est marquée comme résolue
     * (lastScoredGameIndex = gamesPlayed) sans incrémenter le score d'aucun joueur.
     */
    private fun computeScoredSalonState(
        session: OnlineGameSession,
        winnerRole: Player?
    ): Pair<Map<String, Int>, Int> {
        val creatorUid = session.creatorUid.takeIf { it.isNotBlank() }
        val opponentUid = session.opponentUid?.takeIf { it.isNotBlank() }

        // Salon incomplet : pas d'écriture dans statsByUid et le marqueur n'avance pas
        if (creatorUid == null || opponentUid == null) {
            val safeStats = if (creatorUid != null) {
                session.statsByUid.filterKeys { it == creatorUid }
            } else {
                emptyMap()
            }
            return safeStats to session.lastScoredGameIndex
        }

        val allowedParticipants = setOf(creatorUid, opponentUid)
        val sanitizedStats = session.statsByUid
            .filterKeys { it in allowedParticipants }
            .toMutableMap()
            .apply {
                putIfAbsent(creatorUid, 0)
                putIfAbsent(opponentUid, 0)
            }

        // Déjà comptabilisé pour cette manche -> idempotent (no-op)
        if (session.lastScoredGameIndex >= session.gamesPlayed) {
            return sanitizedStats to session.lastScoredGameIndex
        }

        if (winnerRole != null) {
            val winnerUid = session.resolveParticipantUidForRole(winnerRole)
            if (winnerUid != null && winnerUid in allowedParticipants) {
                sanitizedStats[winnerUid] = (sanitizedStats[winnerUid] ?: 0) + pointsForWin(session, winnerRole)
            }
        }

        return sanitizedStats to session.gamesPlayed
    }
}
