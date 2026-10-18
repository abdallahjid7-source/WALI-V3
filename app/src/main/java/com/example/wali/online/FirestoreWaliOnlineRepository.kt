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

            val docRef = firestore.collection(COLLECTION_GAMES).document(code)
            val existing = docRef.get().await()
            if (existing.exists()) {
                throw IllegalStateException("Un salon avec ce code existe déjà.")
            }

            val now = System.currentTimeMillis()
            val stateJson = WaliGameSerializer.serializeGameState(initialGameState)
            val playersMap = mapOf(uid to creatorSide.name)

            val data = hashMapOf<String, Any?>(
                FIELD_CODE to code,
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
                FIELD_DEFEAT_AT_TWO_PIECES to initialGameState.ruleOptions.defeatAtTwoPieces
            )

            docRef.set(data).await()

            OnlineGameSession(
                code = code,
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
                abandonedBy = null
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

                transaction.update(
                    docRef,
                    mapOf(
                        FIELD_STATUS to OnlineGameStatus.ONGOING.value,
                        FIELD_OPPONENT_UID to uid,
                        FIELD_OPPONENT_PLAYER to opponentPlayer.name,
                        FIELD_PLAYERS to updatedPlayers,
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

            val updates = mutableMapOf<String, Any?>(
                FIELD_GAME_STATE_JSON to stateJson,
                FIELD_LAST_MOVE_AT to now
            )

            if (newGameState.winner != null) {
                updates[FIELD_STATUS] = OnlineGameStatus.FINISHED.value
                updates[FIELD_WINNER] = newGameState.winner.name
            }

            docRef.update(updates).await()
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

            val updates = mapOf(
                FIELD_STATUS to OnlineGameStatus.ABANDONED.value,
                FIELD_WINNER to winnerPlayer.name,
                FIELD_ABANDONED_BY to myUid,
                FIELD_LAST_MOVE_AT to now
            )

            docRef.update(updates).await()
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

            val status = snapshot.getString(FIELD_STATUS)
            if (status == OnlineGameStatus.WAITING.value) {
                // Si la partie était en attente, la supprimer pour nettoyer le lobby
                docRef.delete().await()
            }
            Unit
        }
    }

    private fun parseSession(doc: DocumentSnapshot): OnlineGameSession? {
        val code = doc.getString(FIELD_CODE) ?: doc.id
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

        return OnlineGameSession(
            code = code,
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
            abandonedBy = abandonedBy
        )
    }
}
