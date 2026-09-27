package com.example.agent

import android.util.Log
import com.example.BuildConfig
import com.example.agent.tracker.TokenUsage
import com.example.agent.tracker.TokenUsageTracker
import com.example.data.BaseColor
import com.example.ui.ColorViewModel
import com.example.utils.ColorUtils
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
 * Résultat d'une interaction avec l'Agent IA
 */
data class AgentResponse(
    val explanation: String,
    val actionsExecuted: List<String>,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val tokenUsage: TokenUsage? = null
)

/**
 * Service de l'Agent IA : Fait le pont (Wrapper) entre les intentions
 * de Gemini et les méthodes réelles de ColorViewModel.
 */
class ColorAgentService(private val viewModel: ColorViewModel) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Traite un message utilisateur, appelle Gemini et applique les actions requises.
     */
    suspend fun processQuery(
        userPrompt: String,
        allColors: List<BaseColor>,
        nuancierColors: List<BaseColor>
    ): AgentResponse = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank()) {
            return@withContext AgentResponse(
                explanation = "Clé API Gemini non trouvée dans la configuration (BuildConfig.GEMINI_API_KEY).",
                actionsExecuted = emptyList(),
                isSuccess = false,
                errorMessage = "API_KEY_MISSING"
            )
        }

        try {
            val catalogDescription = allColors.joinToString(", ") { "${it.name} (${it.pigmentCode ?: it.hexCode})" }
            val nuancierDescription = nuancierColors.joinToString(", ") { it.name }
            val standardPigmentsDescription = ColorUtils.STANDARD_PIGMENTS.joinToString("; ") {
                "${it.name} [${it.code}, ${it.refHex}, ${it.opacity.label}]"
            }

            val fullPrompt = """
${ColorAgentTools.TOOLS_SYSTEM_PROMPT}

ÉTAT ACTUEL DE L'APPLICATION (PRIORITÉ 1 POUR LE TRAVAIL EN COURS) :
- Couleurs disponibles dans la Palette (${allColors.size}) : $catalogDescription
- Couleurs actuellement actives dans le Nuancier (${nuancierColors.size}) : $nuancierDescription

RÉPERTOIRE DES PIGMENTS D'ARTISTE DE RÉFÉRENCE (BIBLIOTHÈQUE SECONDAIRE / SUR DEMANDE) :
$standardPigmentsDescription

DEMANDE DU PEINTRE :
"$userPrompt"
            """.trimIndent()

            // Construction du corps JSON pour l'API Gemini REST
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", fullPrompt)
                            })
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

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
                    Log.w("ColorAgentService", "Tentative $attempt/$maxRetries échouée (HTTP $lastResponseCode). Nouvelle tentative...")
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
                Log.e("ColorAgentService", "Erreur Gemini HTTP $lastResponseCode: $responseBody")
                val friendlyMsg = when (lastResponseCode) {
                    503 -> "Les serveurs Gemini sont momentanément saturés (Erreur 503). Veuillez réessayer dans quelques secondes."
                    429 -> "Limite de requêtes atteinte (Erreur 429). Veuillez patienter un court instant."
                    else -> "Erreur lors de la communication avec Gemini ($lastResponseCode)."
                }
                return@withContext AgentResponse(
                    explanation = friendlyMsg,
                    actionsExecuted = emptyList(),
                    isSuccess = false,
                    errorMessage = "HTTP_$lastResponseCode"
                )
            }

            val jsonResponse = JSONObject(responseBody)
            val tokenUsage = TokenUsageTracker.recordFromResponse(null, jsonResponse)
            val rawText = jsonResponse
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: "Aucune réponse reçue."

            val (cleanExplanation, jsonBlock) = parseResponse(rawText)
            val executedActions = mutableListOf<String>()

            if (jsonBlock != null) {
                executedActions.addAll(executeActions(jsonBlock, allColors))
            }

            AgentResponse(
                explanation = cleanExplanation,
                actionsExecuted = executedActions,
                isSuccess = true,
                tokenUsage = tokenUsage
            )
        } catch (e: Exception) {
            Log.e("ColorAgentService", "Erreur lors de l'exécution de l'agent", e)
            AgentResponse(
                explanation = "Une erreur s'est produite : ${e.localizedMessage}",
                actionsExecuted = emptyList(),
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Sépare le texte explicatif du bloc JSON d'actions.
     */
    private fun parseResponse(rawText: String): Pair<String, String?> {
        val jsonRegex = "```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```".toRegex()
        val match = jsonRegex.find(rawText)

        return if (match != null) {
            val jsonContent = match.groupValues[1]
            val textWithoutJson = rawText.replace(match.value, "").trim()
            Pair(textWithoutJson, jsonContent)
        } else {
            Pair(rawText.trim(), null)
        }
    }

    /**
     * Exécute les actions JSON générées par l'IA sur le ViewModel.
     */
    private suspend fun executeActions(jsonString: String, allColors: List<BaseColor>): List<String> {
        val actionsLog = mutableListOf<String>()
        try {
            val root = JSONObject(jsonString)
            val actionsArray = root.optJSONArray("actions") ?: return actionsLog

            for (i in 0 until actionsArray.length()) {
                val actionObj = actionsArray.getJSONObject(i)
                when (actionObj.optString("action")) {
                    "configurer_nuancier" -> {
                        val couleursArray = actionObj.optJSONArray("couleurs") ?: continue
                        val names = mutableListOf<String>()
                        for (j in 0 until couleursArray.length()) {
                            names.add(couleursArray.getString(j))
                        }

                        val targetColors = mutableListOf<BaseColor>()
                        for (name in names) {
                            val match = allColors.find {
                                it.name.equals(name, ignoreCase = true) ||
                                it.name.contains(name, ignoreCase = true)
                            }
                            if (match != null) {
                                targetColors.add(match)
                            } else {
                                val closestPigment = ColorUtils.STANDARD_PIGMENTS.find {
                                    it.name.contains(name, ignoreCase = true)
                                }
                                val hex = closestPigment?.refHex ?: "#777777"
                                viewModel.addCustomBaseColor(name, hex)
                            }
                        }

                        viewModel.setNuancierActiveColors(targetColors.map { it.id }.toSet())
                        actionsLog.add("Nuancier configuré avec ${targetColors.size} couleur(s) : ${names.joinToString(", ")}")
                    }

                    "creer_et_ajouter_au_nuancier" -> {
                        val nom = actionObj.optString("nom")
                        val codeHex = actionObj.optString("code_hex", "#FFFFFF")
                        if (nom.isNotBlank()) {
                            viewModel.addCustomBaseColor(nom, codeHex)
                            actionsLog.add("Nouvelle couleur créée et ajoutée au nuancier : $nom ($codeHex)")
                        }
                    }

                    "appliquer_recette_melange" -> {
                        val recetteArray = actionObj.optJSONArray("recette") ?: continue
                        viewModel.clearMixer()
                        for (j in 0 until minOf(recetteArray.length(), 6)) {
                            val item = recetteArray.getJSONObject(j)
                            val nom = item.optString("nom")
                            val parts = item.optInt("parts", 1)

                            val color = allColors.find { it.name.contains(nom, ignoreCase = true) }
                            if (color != null) {
                                viewModel.setMixerSlotColorAndProportion(color.id, parts)
                            }
                        }
                        actionsLog.add("Recette appliquée dans le mélangeur")
                    }

                    "creer_et_enregistrer_melange" -> {
                        val nomMelange = actionObj.optString("nom", "Mélange IA")
                        val recetteArray = actionObj.optJSONArray("recette") ?: continue
                        val recipeItems = mutableListOf<com.example.data.RecipeItem>()

                        viewModel.clearMixer()
                        for (j in 0 until minOf(recetteArray.length(), 6)) {
                            val item = recetteArray.getJSONObject(j)
                            val compNom = item.optString("nom")
                            val parts = item.optInt("parts", 1)
                            val customHex = item.optString("code_hex", "")

                            // Trouver ou créer la couleur dans les bases
                            var matchColor = allColors.find {
                                it.name.equals(compNom, ignoreCase = true) || it.name.contains(compNom, ignoreCase = true)
                            }
                            if (matchColor == null) {
                                val closestPigment = ColorUtils.STANDARD_PIGMENTS.find {
                                    it.name.contains(compNom, ignoreCase = true)
                                }
                                val finalHex = if (customHex.isNotBlank()) customHex else (closestPigment?.refHex ?: "#888888")
                                viewModel.addCustomBaseColor(compNom, finalHex)
                                matchColor = BaseColor(
                                    id = (1000 + j), // ID de référence temporaire si insertion en cours
                                    name = compNom,
                                    hexCode = finalHex
                                )
                            }

                            recipeItems.add(
                                com.example.data.RecipeItem(
                                    colorId = matchColor.id,
                                    name = matchColor.name,
                                    hexCode = matchColor.hexCode,
                                    parts = parts
                                )
                            )

                            // Configure également le mélangeur en direct
                            viewModel.setMixerSlotColorAndProportion(matchColor.id, parts)
                        }

                        if (recipeItems.isNotEmpty()) {
                            viewModel.createAndSaveMixtureDirectly(nomMelange, recipeItems)
                            actionsLog.add("Mélange \"$nomMelange\" enregistré dans l'onglet Mélanges avec ses ${recipeItems.size} composantes")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ColorAgentService", "Erreur lors du décodage des actions JSON", e)
        }
        return actionsLog
    }
}
