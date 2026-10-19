package com.example.wali.online

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fournisseur centralisé de l'instance Cloud Firestore pour Wali Songhaï.
 *
 * Utilise la base de données Firestore par défaut du projet `wali-6fa2a`.
 */
object WaliFirebaseProvider {

    @Suppress("UNUSED_PARAMETER")
    fun getFirestore(context: Context? = null): FirebaseFirestore {
        return FirebaseFirestore.getInstance().also { db ->
            runCatching { db.enableNetwork() }
        }
    }
}
