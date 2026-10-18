package com.example.wali.chat

import android.util.Log
import com.example.wali.model.Player
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implémentation Firestore de [WaliChatRepository].
 *
 * Utilise la sous-collection `games/{gameCode}/messages` et la collection `reports`.
 * Toutes les opérations sont encapsulées pour garantir qu'aucune exception réseau ne fasse planter l'app.
 */
class FirestoreWaliChatRepository(
    private val firestore: FirebaseFirestore = com.example.wali.online.WaliFirebaseProvider.getFirestore()
) : WaliChatRepository {

    companion object {
        private const val TAG = "FirestoreWaliChat"
        private const val COLLECTION_GAMES = "games"
        private const val SUBCOLLECTION_MESSAGES = "messages"
        private const val COLLECTION_REPORTS = "reports"

        private const val FIELD_SENDER_ROLE = "senderRole"
        private const val FIELD_SENDER_UID = "senderUid"
        private const val FIELD_TEXT = "text"
        private const val FIELD_TYPE = "type"
        private const val FIELD_TIMESTAMP = "timestamp"
    }

    override fun observeMessages(gameCode: String): Flow<List<WaliChatMessage>> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val messagesRef = firestore.collection(COLLECTION_GAMES)
            .document(gameCode)
            .collection(SUBCOLLECTION_MESSAGES)
            .orderBy(FIELD_TIMESTAMP, Query.Direction.ASCENDING)

        val registration = messagesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Erreur écoute des messages pour $gameCode : ${error.message}")
                // Échec silencieux, ne crash pas
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val messages = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val senderRoleStr = doc.getString(FIELD_SENDER_ROLE)
                        val senderRole = senderRoleStr?.let { runCatching { Player.valueOf(it) }.getOrNull() }
                        val senderUid = doc.getString(FIELD_SENDER_UID) ?: ""
                        val text = doc.getString(FIELD_TEXT) ?: ""
                        val typeStr = doc.getString(FIELD_TYPE) ?: ChatMessageType.FREE_TEXT.name
                        val type = runCatching { ChatMessageType.valueOf(typeStr) }.getOrDefault(ChatMessageType.FREE_TEXT)

                        val timestamp = doc.getDate(FIELD_TIMESTAMP)?.time
                            ?: doc.getLong(FIELD_TIMESTAMP)
                            ?: System.currentTimeMillis()

                        WaliChatMessage(
                            id = id,
                            senderRole = senderRole,
                            senderUid = senderUid,
                            text = text,
                            timestamp = timestamp,
                            type = type
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Erreur parsing message ${doc.id} : ${e.message}")
                        null
                    }
                }
                trySend(messages)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun sendMessage(gameCode: String, message: WaliChatMessage): Result<Unit> {
        return runCatching {
            if (gameCode.isBlank()) {
                throw IllegalArgumentException("Code de partie manquant.")
            }

            val validatedText = WaliChatMessage.validateText(message.text)
                ?: throw IllegalArgumentException("Texte de message invalide ou vide.")

            val messagesRef = firestore.collection(COLLECTION_GAMES)
                .document(gameCode)
                .collection(SUBCOLLECTION_MESSAGES)

            val data = hashMapOf(
                FIELD_SENDER_ROLE to (message.senderRole?.name ?: ""),
                FIELD_SENDER_UID to message.senderUid,
                FIELD_TEXT to validatedText,
                FIELD_TYPE to message.type.name,
                FIELD_TIMESTAMP to FieldValue.serverTimestamp()
            )

            messagesRef.add(data).await()
            Unit
        }.onFailure { e ->
            Log.w(TAG, "Échec envoi message (silencieux) : ${e.message}")
        }
    }

    override suspend fun submitReport(report: WaliReport): Result<Unit> {
        return runCatching {
            val reportsRef = firestore.collection(COLLECTION_REPORTS)
            val data = hashMapOf(
                "gameCode" to report.gameCode,
                "reportedUid" to report.reportedUid,
                "reportedRole" to report.reportedRole,
                "reporterUid" to report.reporterUid,
                "reporterRole" to report.reporterRole,
                "reason" to report.reason,
                "recentMessages" to report.recentMessages,
                "timestamp" to FieldValue.serverTimestamp()
            )
            reportsRef.add(data).await()
            Unit
        }.onFailure { e ->
            Log.w(TAG, "Échec enregistrement signalement (silencieux) : ${e.message}")
        }
    }

    override suspend fun clearMessages(gameCode: String): Result<Unit> {
        return runCatching {
            if (gameCode.isBlank()) return Result.success(Unit)
            val messagesRef = firestore.collection(COLLECTION_GAMES)
                .document(gameCode)
                .collection(SUBCOLLECTION_MESSAGES)

            val snapshot = messagesRef.get().await()
            for (doc in snapshot.documents) {
                runCatching { doc.reference.delete().await() }
            }
            Unit
        }
    }
}
