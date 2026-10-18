package com.example.wali.model

/**
 * Mode de jeu :
 * - TWO_PLAYERS : Deux joueurs humains sur le même appareil (local).
 * - VS_COMPUTER : Humain contre l'ordinateur.
 * - ONLINE : Deux joueurs humains à distance via Firestore.
 */
enum class GameMode {
    TWO_PLAYERS,
    VS_COMPUTER,
    ONLINE
}
