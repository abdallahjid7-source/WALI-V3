package com.example.wali.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire centralisé des annonces Google AdMob pour le jeu Wali Songhaï.
 *
 * Implémente une politique stricte de tolérance aux pannes :
 * - Aucune erreur réseau ou d'indisponibilité de publicité ne bloque le joueur ou ne fait planter l'application.
 * - Préchargement systématique en arrière-plan.
 * - Intégration des blocs officiels AdMob (App Open, Interstitiel, Récompensé).
 */
object WaliAdManager {

    private const val TAG = "WaliAdManager"

    // =========================================================================================
    // IDENTIFIANTS OFFICIELS GOOGLE ADMOB DE WALI SONGHAÏ
    // =========================================================================================

    /** wali-ouverture-ads */
    const val APP_OPEN_AD_ID = "ca-app-pub-4004787474021292/9389673307"

    /** wali-interstitiel */
    const val INTERSTITIAL_AD_ID = "ca-app-pub-4004787474021292/1518439646"

    /** wali-reward */
    const val REWARDED_AD_ID = "ca-app-pub-4004787474021292/1055405217"

    // Alias pour compatibilité
    const val TEST_APP_OPEN_AD_ID = APP_OPEN_AD_ID
    const val TEST_INTERSTITIAL_AD_ID = INTERSTITIAL_AD_ID

    // =========================================================================================

    val frequencyPolicy = WaliAdFrequencyPolicy(gamesBetweenAds = 2, minBackgroundDurationHours = 4L)

    private val adScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenLoading = false

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    private val _isAdActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAdActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isAdActive

    var isShowingAd: Boolean = false
        private set

    private fun setAdShowing(showing: Boolean) {
        isShowingAd = showing
        _isAdActive.value = showing
    }

    private var isSdkInitialized = false

    /**
     * Détecte si l'application s'exécute dans un émulateur cloud sans nœud DRM matériel (/dev/dri/renderD128)
     * ou sans service système Privacy Sandbox AdServices afin d'éviter l'instanciation de WebView Chromium
     * en arrière-plan (qui provoque E/MESA et E/adservices).
     */
    private fun isHeadlessOrEmulatorEnvironment(): Boolean {
        return runCatching {
            val fingerprint = android.os.Build.FINGERPRINT.lowercase()
            val hardware = android.os.Build.HARDWARE.lowercase()
            val model = android.os.Build.MODEL.lowercase()
            val noRenderNode = !java.io.File("/dev/dri/renderD128").exists()
            noRenderNode ||
                fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                hardware.contains("ranchu") ||
                hardware.contains("goldfish") ||
                model.contains("sdk_gphone") ||
                model.contains("emulator") ||
                model.contains("android sdk built for")
        }.getOrDefault(false)
    }

    /**
     * Initialise le SDK MobileAds de manière asynchrone sans jamais bloquer le thread principal.
     */
    fun initialize(context: Context) {
        if (isSdkInitialized) return
        if (isHeadlessOrEmulatorEnvironment()) {
            isSdkInitialized = true
            Log.d(TAG, "Environnement émulateur détecté : initialisation MobileAds ignorée pour éviter E/adservices et E/MESA.")
            return
        }
        adScope.launch(Dispatchers.IO) {
            runCatching {
                val requestConfiguration = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfiguration)

                MobileAds.initialize(context.applicationContext) { initStatus ->
                    isSdkInitialized = true
                    Log.d(TAG, "Google Mobile Ads SDK initialisé avec succès: $initStatus")
                    preloadAppOpenAd(context.applicationContext)
                }
            }.onFailure { error ->
                Log.w(TAG, "Erreur non bloquante lors de l'initialisation de MobileAds: ${error.message}")
            }
        }
    }

    /**
     * Précharge une annonce App Open en arrière-plan.
     */
    fun preloadAppOpenAd(context: Context) {
        if (isHeadlessOrEmulatorEnvironment()) return
        if (appOpenAd != null || isAppOpenLoading) return
        isAppOpenLoading = true

        adScope.launch {
            runCatching {
                val request = AdRequest.Builder().build()
                AppOpenAd.load(
                    context.applicationContext,
                    APP_OPEN_AD_ID,
                    request,
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            appOpenAd = ad
                            isAppOpenLoading = false
                            Log.d(TAG, "App Open Ad préchargée avec succès.")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            appOpenAd = null
                            isAppOpenLoading = false
                            Log.w(TAG, "Échec chargement App Open Ad (silencieux): ${loadAdError.message}")
                        }
                    }
                )
            }.onFailure { error ->
                isAppOpenLoading = false
                Log.w(TAG, "Exception lors de la requête App Open: ${error.message}")
            }
        }
    }

    /**
     * Précharge une annonce interstitielle en arrière-plan.
     */
    fun preloadInterstitialAd(context: Context) {
        if (isHeadlessOrEmulatorEnvironment()) return
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        adScope.launch {
            runCatching {
                val request = AdRequest.Builder().build()
                InterstitialAd.load(
                    context.applicationContext,
                    INTERSTITIAL_AD_ID,
                    request,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                            isInterstitialLoading = false
                            Log.d(TAG, "Interstitielle préchargée avec succès.")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                            Log.w(TAG, "Échec chargement Interstitielle (silencieux): ${loadAdError.message}")
                        }
                    }
                )
            }.onFailure { error ->
                isInterstitialLoading = false
                Log.w(TAG, "Exception lors de la requête Interstitielle: ${error.message}")
            }
        }
    }

    /**
     * Précharge une annonce récompensée en arrière-plan.
     */
    fun preloadRewardedAd(context: Context) {
        if (isHeadlessOrEmulatorEnvironment()) return
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        adScope.launch {
            runCatching {
                val request = AdRequest.Builder().build()
                RewardedAd.load(
                    context.applicationContext,
                    REWARDED_AD_ID,
                    request,
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            rewardedAd = ad
                            isRewardedLoading = false
                            Log.d(TAG, "Rewarded Ad préchargée avec succès.")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            rewardedAd = null
                            isRewardedLoading = false
                            Log.w(TAG, "Échec chargement Rewarded Ad (silencieux): ${loadAdError.message}")
                        }
                    }
                )
            }.onFailure { error ->
                isRewardedLoading = false
                Log.w(TAG, "Exception lors de la requête Rewarded Ad: ${error.message}")
            }
        }
    }

    /** Indique si une annonce récompensée est actuellement prête à être affichée. */
    fun isRewardedAdReady(): Boolean = rewardedAd != null

    /**
     * Affiche une annonce récompensée (Rewarded Ad) à la demande du joueur.
     *
     * @param activity L'activité hôte.
     * @param onUserEarnedReward Callback déclenché lorsque le joueur a visionné l'annonce complète.
     * @param onFinished Callback appelé à la fermeture ou en cas d'impossibilité d'affichage.
     */
    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad != null && !isShowingAd && !activity.isFinishing && !activity.isDestroyed) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    setAdShowing(true)
                }

                override fun onAdDismissedFullScreenContent() {
                    setAdShowing(false)
                    rewardedAd = null
                    preloadRewardedAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Échec affichage Rewarded Ad: ${adError.message}")
                    setAdShowing(false)
                    rewardedAd = null
                    preloadRewardedAd(activity.applicationContext)
                    onFinished()
                }
            }
            runCatching {
                ad.show(activity, OnUserEarnedRewardListener { rewardItem ->
                    Log.d(TAG, "Récompense attribuée: ${rewardItem.amount} ${rewardItem.type}")
                    onUserEarnedReward(rewardItem)
                })
            }.onFailure { e ->
                Log.w(TAG, "Exception lors de ad.show Rewarded: ${e.message}")
                setAdShowing(false)
                rewardedAd = null
                preloadRewardedAd(activity.applicationContext)
                onFinished()
            }
        } else {
            preloadRewardedAd(activity.applicationContext)
            onFinished()
        }
    }

    /**
     * Affiche l'annonce App Open au démarrage à froid, avant l'affichage du premier dialogue.
     * Si l'annonce n'est pas encore prête, accorde un court délai non bloquant (max 1000ms),
     * puis poursuit silencieusement le jeu si elle n'est toujours pas disponible.
     */
    fun showColdStartAppOpenAd(activity: Activity, onFinished: () -> Unit) {
        adScope.launch {
            // Attente courte si le chargement initial est en cours
            var attempts = 0
            while (appOpenAd == null && (!isSdkInitialized || isAppOpenLoading) && attempts < 15) {
                delay(100)
                attempts++
            }

            val ad = appOpenAd
            if (ad != null && !isShowingAd && !activity.isFinishing && !activity.isDestroyed) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        setAdShowing(true)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        setAdShowing(false)
                        appOpenAd = null
                        preloadAppOpenAd(activity.applicationContext)
                        onFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Échec affichage App Open (silencieux): ${adError.message}")
                        setAdShowing(false)
                        appOpenAd = null
                        preloadAppOpenAd(activity.applicationContext)
                        onFinished()
                    }
                }
                runCatching {
                    ad.show(activity)
                }.onFailure { e ->
                    Log.w(TAG, "Exception lors de ad.show: ${e.message}")
                    setAdShowing(false)
                    appOpenAd = null
                    preloadAppOpenAd(activity.applicationContext)
                    onFinished()
                }
            } else {
                // Pas de pub prête ou disponible : continuation fluide immédiate
                onFinished()
            }
        }
    }

    /**
     * Tente d'afficher une annonce App Open lorsque l'application revient au premier plan
     * après un temps prolongé en arrière-plan (>= 4 heures), sans perturber une partie active.
     */
    fun showAppOpenOnForegroundIfEligible(activity: Activity) {
        if (!frequencyPolicy.shouldShowAppOpenOnForeground() || isShowingAd) return

        val ad = appOpenAd ?: run {
            preloadAppOpenAd(activity.applicationContext)
            return
        }

        frequencyPolicy.clearBackgroundTime()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                setAdShowing(true)
            }

            override fun onAdDismissedFullScreenContent() {
                setAdShowing(false)
                appOpenAd = null
                preloadAppOpenAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Échec affichage App Open en reprise: ${adError.message}")
                setAdShowing(false)
                appOpenAd = null
                preloadAppOpenAd(activity.applicationContext)
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure { e ->
            Log.w(TAG, "Exception lors de ad.show au retour premier plan: ${e.message}")
            setAdShowing(false)
            appOpenAd = null
            preloadAppOpenAd(activity.applicationContext)
        }
    }

    /**
     * Affiche une annonce interstitielle après la fermeture du dialogue de fin de partie,
     * selon la politique de fréquence définie (toutes les 2-3 parties terminées).
     *
     * @param onFinished Callback invoqué dès la fin de l'annonce ou immédiatement si non éligible/indisponible.
     */
    fun showInterstitialIfEligible(activity: Activity, onFinished: () -> Unit) {
        val shouldShow = frequencyPolicy.onGameFinished()

        if (!shouldShow) {
            onFinished()
            return
        }

        val ad = interstitialAd
        if (ad != null && !isShowingAd && !activity.isFinishing && !activity.isDestroyed) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    setAdShowing(true)
                }

                override fun onAdDismissedFullScreenContent() {
                    setAdShowing(false)
                    interstitialAd = null
                    preloadInterstitialAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Échec affichage Interstitielle (silencieux): ${adError.message}")
                    setAdShowing(false)
                    interstitialAd = null
                    preloadInterstitialAd(activity.applicationContext)
                    onFinished()
                }
            }
            runCatching {
                ad.show(activity)
            }.onFailure { e ->
                Log.w(TAG, "Exception lors de ad.show interstitielle: ${e.message}")
                setAdShowing(false)
                interstitialAd = null
                preloadInterstitialAd(activity.applicationContext)
                onFinished()
            }
        } else {
            // L'annonce n'est pas encore prête ou réseau indisponible : on relance un préchargement et on continue
            preloadInterstitialAd(activity.applicationContext)
            onFinished()
        }
    }
}
