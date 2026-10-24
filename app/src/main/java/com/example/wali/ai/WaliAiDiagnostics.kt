package com.example.wali.ai

import android.util.Log
import com.example.wali.model.Phase
import com.example.wali.model.Player

/**
 * Enregistrement de diagnostic pour un coup joué par l'IA en partie réelle.
 */
data class AiTurnDiagnostic(
    val id: Int,
    val timestamp: Long,
    val player: Player,
    val phase: Phase,
    val difficulty: AiDifficulty,
    val thinkBudgetMs: Long,
    val searchTimeMs: Long,
    val completedDepth: Int,
    val nodeCount: Long,
    val nodesPerSecond: Long,
    val moveSummary: String,
    val shortcutReason: String?,
    val isProfilingEnabled: Boolean
) {
    fun formatLogcat(): String =
        "AI_TURN #$id | player=$player | phase=$phase | diff=$difficulty | " +
        "budget=${thinkBudgetMs}ms | time=${searchTimeMs}ms | depth=$completedDepth | " +
        "nodes=$nodeCount | nps=$nodesPerSecond | profiling=$isProfilingEnabled | " +
        "shortcut=${shortcutReason ?: "NONE (recherche complète)"} | coup=$moveSummary"

    fun formatDisplay(): String {
        val playerName = if (player == Player.STICKS) "Bâtonnets" else "Cailloux"
        return "#$id [$playerName - $difficulty]\n" +
            "• Coup : $moveSummary\n" +
            "• Type : ${shortcutReason ?: "Recherche complète (Approfondissement itératif)"}\n" +
            "• Profondeur : $completedDepth plis | Nœuds : $nodeCount (${nodesPerSecond} nps)\n" +
            "• Temps : ${searchTimeMs} ms (budget max : ${thinkBudgetMs} ms)\n" +
            "• isProfilingEnabled : $isProfilingEnabled"
    }
}

/**
 * Journaliseur et gestionnaire de diagnostic en direct des décisions de l'IA en partie réelle.
 */
object WaliAiDiagnostics {
    private const val MAX_HISTORY = 25
    private val historyList = mutableListOf<AiTurnDiagnostic>()
    private var globalTurnCounter = 0

    @Synchronized
    fun recordTurn(
        player: Player,
        phase: Phase,
        difficulty: AiDifficulty,
        thinkBudgetMs: Long,
        searchTimeMs: Long,
        completedDepth: Int,
        nodeCount: Long,
        moveSummary: String,
        shortcutReason: String?,
        isProfilingEnabled: Boolean
    ): AiTurnDiagnostic {
        globalTurnCounter++
        val nps = if (searchTimeMs > 0) (nodeCount * 1000L) / searchTimeMs else 0L
        val diag = AiTurnDiagnostic(
            id = globalTurnCounter,
            timestamp = System.currentTimeMillis(),
            player = player,
            phase = phase,
            difficulty = difficulty,
            thinkBudgetMs = thinkBudgetMs,
            searchTimeMs = searchTimeMs,
            completedDepth = completedDepth,
            nodeCount = nodeCount,
            nodesPerSecond = nps,
            moveSummary = moveSummary,
            shortcutReason = shortcutReason,
            isProfilingEnabled = isProfilingEnabled
        )
        historyList.add(0, diag)
        if (historyList.size > MAX_HISTORY) {
            historyList.removeAt(historyList.size - 1)
        }

        // Trace dédiée dans adb logcat sous le tag WaliAiDiag
        Log.i("WaliAiDiag", diag.formatLogcat())
        return diag
    }

    @Synchronized
    fun getHistory(): List<AiTurnDiagnostic> = historyList.toList()

    @Synchronized
    fun clearHistory() {
        historyList.clear()
        globalTurnCounter = 0
    }
}
