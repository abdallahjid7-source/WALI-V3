package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.wali.ads.WaliAdManager

/**
 * Classe Application principale du jeu Wali Songhaï.
 *
 * Responsabilités :
 * - Initialisation asynchrone et non bloquante de Google Mobile Ads SDK au démarrage.
 * - Suivi du cycle de vie de l'application (premier plan / arrière-plan) pour le calcul
 *   du délai de reprise (4 heures) avant affichage d'une annonce App Open.
 */
class WaliApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var startedActivitiesCount = 0
    var currentActivity: Activity? = null
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)

        // Assure l'existence et les permissions de l'arborescence de cache WebView Chromium
        runCatching {
            val baseCache = cacheDir
            val webViewDir = java.io.File(baseCache, "WebView")
            val defaultDir = java.io.File(webViewDir, "Default")
            val httpCache = java.io.File(defaultDir, "HTTP Cache")
            val codeCache = java.io.File(httpCache, "Code Cache")
            val jsCodeCache = java.io.File(codeCache, "js")
            val wasmCodeCache = java.io.File(codeCache, "wasm")
            
            wasmCodeCache.mkdirs()
            jsCodeCache.mkdirs()
            
            listOf(webViewDir, defaultDir, httpCache, codeCache, jsCodeCache, wasmCodeCache).forEach { dir ->
                dir.setReadable(true, false)
                dir.setWritable(true, false)
                dir.setExecutable(true, false)
            }
        }

        // Initialisation asynchrone non bloquante d'AdMob
        WaliAdManager.initialize(this)

        // Initialisation de Firebase et Authentification Anonyme automatique
        runCatching {
            com.google.firebase.FirebaseApp.initializeApp(this)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        android.util.Log.d("WaliApp", "Connexion anonyme Firebase réussie : ${it.user?.uid}")
                    }
                    .addOnFailureListener { exception ->
                        android.util.Log.w("WaliApp", "Connexion anonyme différée : ${exception.message}")
                    }
            }
        }.onFailure { exception ->
            android.util.Log.w("WaliApp", "Initialisation Firebase différée : ${exception.message}")
        }
    }

    private fun isAdActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("AdActivity") || name.contains("com.google.android.gms.ads")
    }

    override fun onActivityStarted(activity: Activity) {
        if (isAdActivity(activity)) return
        currentActivity = activity
        if (startedActivitiesCount == 0) {
            // L'application revient au premier plan depuis l'arrière-plan
            WaliAdManager.showAppOpenOnForegroundIfEligible(activity)
        }
        startedActivitiesCount++
    }

    override fun onActivityStopped(activity: Activity) {
        if (isAdActivity(activity)) return
        startedActivitiesCount--
        if (startedActivitiesCount <= 0) {
            startedActivitiesCount = 0
            // L'application passe entièrement en arrière-plan
            WaliAdManager.frequencyPolicy.recordBackgroundTime(System.currentTimeMillis())
        }
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isAdActivity(activity)) {
            currentActivity = activity
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }
}
