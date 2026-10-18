package com.example.wali.chat

/**
 * État de l'interface du chat multijoueur.
 */
data class WaliChatUiState(
    val isOpen: Boolean = false,
    val messages: List<WaliChatMessage> = emptyList(),
    val unreadCount: Int = 0,
    val inputText: String = "",
    val isOpponentBlocked: Boolean = false,
    val isConnected: Boolean = true,
    val rateLimitNotice: String? = null,
    val showWarningBanner: Boolean = true,
    val showReportDialog: Boolean = false,
    val reportNotice: String? = null
)
