package com.example.wali.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Gestionnaire d'authentification Google via Jetpack Credential Manager.
 *
 * Conforme aux règles d'architecture Firebase Firestore Android :
 * - Utilisation exclusive de GetSignInWithGoogleOption et de default_web_client_id.
 * - Aucune authentification anonyme ni par mot de passe.
 */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignInHelper"

    val currentUser: FirebaseUser?
        get() = FirebaseAuth.getInstance().currentUser

    val isUserSignedIn: Boolean
        get() = FirebaseAuth.getInstance().currentUser != null

    val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val currentDisplayName: String?
        get() = FirebaseAuth.getInstance().currentUser?.displayName

    val currentEmail: String?
        get() = FirebaseAuth.getInstance().currentUser?.email

    /**
     * Déclenche la boîte de dialogue native Credential Manager pour la connexion Google.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        return runCatching {
            val credentialManager = CredentialManager.create(context)
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val serverClientId = if (resId != 0) context.getString(resId) else ""

            val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("Utilisateur Google introuvable après authentification.")
                Log.d(TAG, "Connexion Google réussie : ${user.uid} (${user.displayName})")
                user
            } else {
                throw IllegalStateException("Type d'identifiant Google inattendu.")
            }
        }.onFailure { e ->
            if (e is GetCredentialCancellationException) {
                Log.d(TAG, "Connexion Google annulée par l'utilisateur.")
            } else {
                Log.e(TAG, "Erreur de connexion Google : ${e.message}", e)
            }
        }
    }

    /**
     * Déconnecte l'utilisateur courant de Firebase Auth.
     */
    fun signOut() {
        runCatching {
            FirebaseAuth.getInstance().signOut()
            Log.d(TAG, "Déconnexion Firebase Auth effectuée.")
        }
    }
}
