package com.example.wali.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.theme.MyApplicationTheme
import com.example.wali.online.OnlineGameStatus
import com.example.wali.ui.online.WaliOnlineScreen
import com.example.wali.ui.WaliGameScreen
import com.example.wali.ui.WaliViewModel
import com.example.wali.ui.rules.WaliRulesScreen
import com.example.wali.ui.scoreboard.WaliScoreboardScreen
import com.example.wali.ui.settings.WaliSettingsScreen

object WaliDestinations {
    const val GAME = "wali_game"
    const val RULES = "wali_rules"
    const val SETTINGS = "wali_settings"
    const val ONLINE = "wali_online"
    const val SCOREBOARD = "wali_scoreboard"
}

/**
 * Composant racine de l'application gérant la navigation fluide entre l'écran de jeu,
 * l'écran des règles du jeu, l'écran des réglages et le salon multijoueur en ligne.
 *
 * Le [WaliViewModel] est conservé au niveau de l'application pour garantir que
 * la consultation des règles, des réglages ou du lobby n'interrompt ni ne réinitialise la partie.
 */
@Composable
fun WaliApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val gameViewModel: WaliViewModel = viewModel(
        factory = WaliViewModel.provideFactory(context)
    )
    val uiState by gameViewModel.uiState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Suspend le minuteur de jeu lorsque les règles, les réglages, le lobby ou les scores sont consultés
    LaunchedEffect(currentRoute) {
        val isSecondary = currentRoute == WaliDestinations.RULES || 
                          currentRoute == WaliDestinations.SETTINGS || 
                          currentRoute == WaliDestinations.ONLINE ||
                          currentRoute == WaliDestinations.SCOREBOARD
        gameViewModel.setSecondaryScreenOpen(isSecondary)
    }

    // Bascule automatique vers l'échiquier dès qu'un adversaire rejoint la session en attente
    LaunchedEffect(uiState.onlineGameSession?.status, currentRoute) {
        if (uiState.onlineGameSession?.status == OnlineGameStatus.ONGOING && currentRoute == WaliDestinations.ONLINE) {
            navController.popBackStack()
        }
    }

    MyApplicationTheme(palette = uiState.themePalette) {
        NavHost(
            navController = navController,
            startDestination = WaliDestinations.GAME,
            modifier = modifier
        ) {
            composable(
                route = WaliDestinations.GAME,
                enterTransition = {
                    fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                }
            ) {
                WaliGameScreen(
                    viewModel = gameViewModel,
                    onOpenRules = {
                        gameViewModel.playButtonClick()
                        navController.navigate(WaliDestinations.RULES)
                    },
                    onOpenSettings = {
                        gameViewModel.playButtonClick()
                        navController.navigate(WaliDestinations.SETTINGS)
                    },
                    onOpenOnline = {
                        gameViewModel.playButtonClick()
                        navController.navigate(WaliDestinations.ONLINE)
                    },
                    onOpenScoreboard = {
                        gameViewModel.playButtonClick()
                        navController.navigate(WaliDestinations.SCOREBOARD)
                    }
                )
            }

            composable(
                route = WaliDestinations.RULES,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(250)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                WaliRulesScreen(
                    onNavigateBack = {
                        gameViewModel.playButtonClick()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = WaliDestinations.SETTINGS,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                WaliSettingsScreen(
                    sfxVolume = uiState.sfxVolume,
                    musicVolume = uiState.musicVolume,
                    isMuted = uiState.isMuted,
                    selectedPalette = uiState.themePalette,
                    isTimerEnabled = uiState.isTimerEnabled,
                    timerDurationSeconds = uiState.timerDurationSeconds,
                    isMandatoryAlignmentEnabled = uiState.isMandatoryAlignmentEnabled,
                    isDefeatAtTwoPiecesEnabled = uiState.isDefeatAtTwoPiecesEnabled,
                    onSfxVolumeChanged = { gameViewModel.onSfxVolumeChanged(it) },
                    onMusicVolumeChanged = { gameViewModel.onMusicVolumeChanged(it) },
                    onMutedChanged = { gameViewModel.onMutedChanged(it) },
                    onThemeSelected = { gameViewModel.onThemeSelected(it) },
                    onTimerEnabledChanged = { gameViewModel.onTimerEnabledChanged(it) },
                    onTimerDurationChanged = { gameViewModel.onTimerDurationChanged(it) },
                    onMandatoryAlignmentChanged = { gameViewModel.onMandatoryAlignmentChanged(it) },
                    onDefeatAtTwoPiecesChanged = { gameViewModel.onDefeatAtTwoPiecesChanged(it) },
                    onOpenScoreboard = {
                        gameViewModel.playButtonClick()
                        navController.navigate(WaliDestinations.SCOREBOARD)
                    },
                    onNavigateBack = {
                        gameViewModel.playButtonClick()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = WaliDestinations.ONLINE,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                val activeOptions = uiState.onlineGameSession?.gameState?.ruleOptions
                WaliOnlineScreen(
                    waitingForOpponentCode = uiState.waitingForOpponentCode,
                    publicWaitingGames = uiState.publicWaitingGames,
                    isLoading = uiState.isOnlineLoading,
                    errorMessage = uiState.onlineErrorMessage,
                    mandatoryAlignment = activeOptions?.mandatoryAlignment ?: uiState.isMandatoryAlignmentEnabled,
                    defeatAtTwoPieces = activeOptions?.defeatAtTwoPieces ?: uiState.isDefeatAtTwoPiecesEnabled,
                    onCreateGame = { isPublic, side ->
                        gameViewModel.startCreateOnlineGame(isPublic, side)
                    },
                    onJoinGame = { code ->
                        gameViewModel.startJoinOnlineGame(code)
                        navController.popBackStack()
                    },
                    onCancelWaiting = {
                        gameViewModel.cancelOnlineWaiting()
                    },
                    onDismissError = {
                        gameViewModel.dismissOnlineError()
                    },
                    onNavigateBack = {
                        gameViewModel.playButtonClick()
                        gameViewModel.cancelOnlineWaiting()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = WaliDestinations.SCOREBOARD,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                WaliScoreboardScreen(
                    stats = uiState.scoreboardStats,
                    recentGames = uiState.recentGames,
                    onResetScoreboard = { gameViewModel.resetScoreboard() },
                    onNavigateBack = {
                        gameViewModel.playButtonClick()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
