package com.example.agent.calc

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.BuildConfig
import com.example.agent.tracker.TokenUsage
import com.example.agent.tracker.TokenUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Résultat retourné par l'Agent après consultation de Gemini.
 * 
 * Contient l'explication textuelle, les actions exécutées et les paramètres
 * extraits par l'IA (couleur hexadécimale, nom de la teinte).
 */
data class LayerAgentResult(
    val explanation: String,
    val actionsExecuted: List<String>,
    val selectedColorHex: String? = null,
    val selectedColorName: String? = null,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val tokenUsage: TokenUsage? = null
)

/**
 * ==================================================================================
 * FICHIER 2 / 3 : LE CERVEAU DE L'AGENT (SERVICE GEMINI AVEC PARAMÈTRES)
 * ==================================================================================
 * 
 * Ce service orchestre la communication entre l'humain, Gemini et vos fonctions :
 * 
 * 1. PRÉPARATION : Concaténation de la description des outils (avec paramètres) + contexte + demande.
 * 2. APPEL REST : Envoi HTTP à Gemini 2.5 Flash via l'API Google Generative Language.
 * 3. EXTRACTION DU PARAMÈTRE : Lecture du bloc JSON pour récupérer "couleur_hex" et "nom_teinte".
 * 4. EXÉCUTION KOTLIN : Conversion de "#704214" en couleur Android et application de la matrice graphique.
 */
class LayerAgentService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processInstruction(
        userPrompt: String,
        activeLayerName: String,
        activeBitmap: Bitmap,
        onApplyTransformedBitmap: (newBitmap: Bitmap, actionDescription: String) -> Unit
    ): LayerAgentResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        // 1. Contrôle strict de la clé API
        if (apiKey.isBlank()) {
            return@withContext LayerAgentResult(
                explanation = "Clé API Gemini non configurée.",
                actionsExecuted = emptyList(),
                isSuccess = false,
                errorMessage = "Pour que Gemini puisse analyser votre demande et déduire les paramètres chromatiques, " +
                        "une clé API Gemini doit être définie dans le panneau Secrets (BuildConfig.GEMINI_API_KEY)."
            )
        }

        // 2. Assemblage du prompt complet (Notice des outils + Contexte + Consigne utilisateur)
        val fullSystemContext = """
${LayerAgentTools.TOOLS_SYSTEM_PROMPT}

---
SITUATION ACTUELLE DE L'APPLICATION :
- Nom du calque sélectionné par l'artiste : "$activeLayerName"
- Résolution du dessin : ${activeBitmap.width} x ${activeBitmap.height} pixels

CONSIGNE DE L'ARTISTE :
"$userPrompt"
""".trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", fullSystemContext)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 4096)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        try {
            // 3. Appel réseau réel à Google Cloud avec gestion anti-503 / anti-saturations (retry avec backoff)
            var lastResponseCode = 0
            var responseBody = ""
            val maxRetries = 3

            for (attempt in 1..maxRetries) {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                lastResponseCode = response.code
                responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    break
                }

                // Si erreur 503 (Service Unavailable), 429 (Rate Limit) ou 500, on attend et on retente
                if (lastResponseCode == 503 || lastResponseCode == 429 || lastResponseCode == 500) {
                    Log.w("LayerAgentService", "Tentative $attempt/$maxRetries échouée (HTTP $lastResponseCode). Nouvelle tentative...")
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(attempt * 1500L) // 1.5s, puis 3s
                        continue
                    }
                } else {
                    // Pour les autres erreurs (ex: 400 ou 403), inutile de retenter
                    break
                }
            }

            if (lastResponseCode !in 200..299) {
                Log.e("LayerAgentService", "Erreur HTTP $lastResponseCode : $responseBody")
                val friendlyMsg = when (lastResponseCode) {
                    503 -> "Les serveurs Gemini sont momentanément saturés (Erreur 503). Veuillez réessayer dans quelques secondes."
                    429 -> "Limite de requêtes atteinte (Erreur 429). Veuillez patienter un court instant."
                    else -> "L'appel à l'API Gemini a retourné une erreur (Code HTTP $lastResponseCode)."
                }
                return@withContext LayerAgentResult(
                    explanation = friendlyMsg,
                    actionsExecuted = emptyList(),
                    isSuccess = false,
                    errorMessage = "HTTP_$lastResponseCode"
                )
            }

            // 4. Lecture de la réponse de Gemini
            val jsonResponse = JSONObject(responseBody)
            val tokenUsage = TokenUsageTracker.recordFromResponse(null, jsonResponse)
            val rawOutput = jsonResponse
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""

            // 5. Découpage : Explication humaine vs Bloc de décision JSON
            val (cleanText, jsonAction) = extractJsonAction(rawOutput)
            val executedActions = mutableListOf<String>()
            var chosenHex: String? = null
            var chosenName: String? = null

            // 6. Exécution de l'outil avec ses paramètres extraits par Gemini
            if (jsonAction != null) {
                val executionInfo = handleJsonAction(jsonAction, activeBitmap, activeLayerName, onApplyTransformedBitmap)
                if (executionInfo != null) {
                    executedActions.add(executionInfo.summary)
                    chosenHex = executionInfo.colorHex
                    chosenName = executionInfo.colorName
                }
            }

            LayerAgentResult(
                explanation = cleanText.ifBlank { "Action exécutée selon vos consignes." },
                actionsExecuted = executedActions,
                selectedColorHex = chosenHex,
                selectedColorName = chosenName,
                isSuccess = true,
                tokenUsage = tokenUsage
            )
        } catch (e: Exception) {
            Log.e("LayerAgentService", "Erreur réseau", e)
            LayerAgentResult(
                explanation = "Impossible de contacter l'API Gemini.",
                actionsExecuted = emptyList(),
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Erreur réseau inconnue"
            )
        }
    }

    private data class ActionExecutionInfo(
        val summary: String,
        val colorHex: String,
        val colorName: String
    )

    /**
     * Sépare le texte explicatif rédigé par Gemini du bloc JSON d'action.
     */
    private fun extractJsonAction(rawResponse: String): Pair<String, String?> {
        val regex = "```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```".toRegex()
        val match = regex.find(rawResponse)
        return if (match != null) {
            val jsonPart = match.groupValues[1]
            val textPart = rawResponse.replace(match.value, "").trim()
            Pair(textPart, jsonPart)
        } else {
            Pair(rawResponse.trim(), null)
        }
    }

    /**
     * Parse le JSON de décision émis par Gemini, extrait les paramètres
     * et appelle la fonction graphique Kotlin correspondante.
     */
    private fun handleJsonAction(
        jsonString: String,
        activeBitmap: Bitmap,
        activeLayerName: String,
        onApplyTransformedBitmap: (newBitmap: Bitmap, actionDescription: String) -> Unit
    ): ActionExecutionInfo? {
        try {
            val root = JSONObject(jsonString)
            val actions = root.optJSONArray("actions") ?: return null

            for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                val actionName = action.optString("action")

                if (actionName == "appliquer_monochrome" || actionName == "convertir_noir_et_blanc") {
                    // Extraction des paramètres décidés par Gemini
                    val colorHex = action.optString("couleur_hex", "#808080")
                    val colorName = action.optString("nom_teinte", "Monochrome")

                    // Conversion du code hexadécimal "#RRGGBB" en Int Android
                    val parsedColor = try {
                        Color.parseColor(colorHex)
                    } catch (e: Exception) {
                        Color.rgb(128, 128, 128) // Fallback si le format hex est invalide
                    }

                    // Appel de la fonction graphique Android dans LayerAgentTools
                    val transformedBitmap = LayerAgentTools.convertBitmapToMonochrome(activeBitmap, parsedColor)
                    
                    val actionLabel = "Monochrome \"$colorName\" ($colorHex) appliqué sur \"$activeLayerName\""
                    onApplyTransformedBitmap(transformedBitmap, actionLabel)

                    return ActionExecutionInfo(
                        summary = actionLabel,
                        colorHex = colorHex,
                        colorName = colorName
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LayerAgentService", "Erreur lors du décodage du JSON de Gemini", e)
        }
        return null
    }
}
