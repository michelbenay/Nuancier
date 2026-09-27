package com.example.agent.tracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Modèle représentant la consommation de tokens pour une requête Gemini.
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val candidatesTokens: Int = 0,
    val totalTokens: Int = 0,
    val thinkingTokens: Int = 0
)

/**
 * Statistiques cumulées de consommation de tokens stockées localement.
 */
data class CumulativeTokenStats(
    val totalTokens: Long = 0L,
    val totalPromptTokens: Long = 0L,
    val totalCandidatesTokens: Long = 0L,
    val totalRequests: Int = 0,
    val lastRequest: TokenUsage? = null,
    val lastTimestampMs: Long = 0L
)

/**
 * Singleton de suivi et persistance de la consommation de tokens pour toutes les requêtes Gemini de l'application.
 */
object TokenUsageTracker {
    private const val PREFS_NAME = "atelier_gemini_token_tracker"
    private const val KEY_TOTAL_TOKENS = "total_tokens"
    private const val KEY_PROMPT_TOKENS = "prompt_tokens"
    private const val KEY_CANDIDATES_TOKENS = "candidates_tokens"
    private const val KEY_TOTAL_REQUESTS = "total_requests"
    private const val KEY_LAST_PROMPT = "last_prompt_tokens"
    private const val KEY_LAST_CANDIDATES = "last_candidates_tokens"
    private const val KEY_LAST_TOTAL = "last_total_tokens"
    private const val KEY_LAST_THINKING = "last_thinking_tokens"
    private const val KEY_LAST_TIMESTAMP = "last_timestamp"

    private var sharedPreferences: SharedPreferences? = null
    private val _statsFlow = MutableStateFlow(CumulativeTokenStats())
    val statsFlow: StateFlow<CumulativeTokenStats> = _statsFlow.asStateFlow()

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadStats()
        }
    }

    private fun loadStats() {
        val prefs = sharedPreferences ?: return
        val total = prefs.getLong(KEY_TOTAL_TOKENS, 0L)
        val prompt = prefs.getLong(KEY_PROMPT_TOKENS, 0L)
        val candidates = prefs.getLong(KEY_CANDIDATES_TOKENS, 0L)
        val requests = prefs.getInt(KEY_TOTAL_REQUESTS, 0)
        val lastPrompt = prefs.getInt(KEY_LAST_PROMPT, 0)
        val lastCandidates = prefs.getInt(KEY_LAST_CANDIDATES, 0)
        val lastTotal = prefs.getInt(KEY_LAST_TOTAL, 0)
        val lastThinking = prefs.getInt(KEY_LAST_THINKING, (lastTotal - lastPrompt - lastCandidates).coerceAtLeast(0))
        val lastTime = prefs.getLong(KEY_LAST_TIMESTAMP, 0L)

        val lastUsage = if (lastTotal > 0) {
            TokenUsage(
                promptTokens = lastPrompt,
                candidatesTokens = lastCandidates,
                totalTokens = lastTotal,
                thinkingTokens = lastThinking
            )
        } else null

        _statsFlow.value = CumulativeTokenStats(
            totalTokens = total,
            totalPromptTokens = prompt,
            totalCandidatesTokens = candidates,
            totalRequests = requests,
            lastRequest = lastUsage,
            lastTimestampMs = lastTime
        )
    }

    /**
     * Extrait les métadonnées de consommation depuis la réponse JSON de Gemini
     * et les enregistre automatiquement dans les statistiques cumulées.
     */
    fun recordFromResponse(context: Context?, jsonResponse: JSONObject): TokenUsage {
        val usageMetadata = jsonResponse.optJSONObject("usageMetadata")
        val prompt = usageMetadata?.optInt("promptTokenCount", 0) ?: 0
        val candidates = usageMetadata?.optInt("candidatesTokenCount", 0) ?: 0
        val total = usageMetadata?.optInt("totalTokenCount", prompt + candidates) ?: (prompt + candidates)
        val thoughts = usageMetadata?.optInt("thoughtsTokenCount", (total - prompt - candidates).coerceAtLeast(0))
            ?: (total - prompt - candidates).coerceAtLeast(0)

        val usage = TokenUsage(
            promptTokens = prompt,
            candidatesTokens = candidates,
            totalTokens = total,
            thinkingTokens = thoughts
        )

        if (total > 0) {
            recordUsage(context, usage)
        }
        return usage
    }

    /**
     * Enregistre manuellement une consommation de tokens.
     */
    fun recordUsage(context: Context?, usage: TokenUsage) {
        if (context != null) {
            init(context)
        }
        val prefs = sharedPreferences ?: return
        val current = _statsFlow.value

        val newTotal = current.totalTokens + usage.totalTokens
        val newPrompt = current.totalPromptTokens + usage.promptTokens
        val newCandidates = current.totalCandidatesTokens + usage.candidatesTokens
        val newRequests = current.totalRequests + 1
        val now = System.currentTimeMillis()

        prefs.edit()
            .putLong(KEY_TOTAL_TOKENS, newTotal)
            .putLong(KEY_PROMPT_TOKENS, newPrompt)
            .putLong(KEY_CANDIDATES_TOKENS, newCandidates)
            .putInt(KEY_TOTAL_REQUESTS, newRequests)
            .putInt(KEY_LAST_PROMPT, usage.promptTokens)
            .putInt(KEY_LAST_CANDIDATES, usage.candidatesTokens)
            .putInt(KEY_LAST_TOTAL, usage.totalTokens)
            .putInt(KEY_LAST_THINKING, usage.thinkingTokens)
            .putLong(KEY_LAST_TIMESTAMP, now)
            .apply()

        _statsFlow.value = CumulativeTokenStats(
            totalTokens = newTotal,
            totalPromptTokens = newPrompt,
            totalCandidatesTokens = newCandidates,
            totalRequests = newRequests,
            lastRequest = usage,
            lastTimestampMs = now
        )
    }

    /**
     * Réinitialise les statistiques de consommation locale.
     */
    fun reset(context: Context?) {
        if (context != null) {
            init(context)
        }
        sharedPreferences?.edit()?.clear()?.apply()
        _statsFlow.value = CumulativeTokenStats()
    }
}
