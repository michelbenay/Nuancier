package com.example.agent.size

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Modèle de pastille de couleur pour un chef-d'œuvre.
 */
data class ArtworkColorSwatch(
    val name: String,
    val hex: String
)

/**
 * Informations complètes sur une œuvre d'art / tableau demandée par l'artiste.
 */
data class ArtworkDisplayInfo(
    val title: String,
    val artist: String,
    val year: String = "",
    val movement: String = "",
    val explanation: String = "",
    val searchTerm: String = "",
    val paletteColors: List<ArtworkColorSwatch> = emptyList(),
    var imageUrl: String? = null,
    var bitmap: Bitmap? = null,
    var sourceName: String = "Google Arts & Culture / Musée"
)

/**
 * Résultat de recherche d'une œuvre d'art avec son URL et sa source vérifiée.
 */
data class ArtworkImageResult(
    val url: String,
    val sourceName: String
)

/**
 * Réponse du Conseiller Artistique, combinant texte pédagogique et œuvre d'art optionnelle.
 */
data class AdvisorReply(
    val textReply: String,
    val artwork: ArtworkDisplayInfo? = null,
    val tokenUsage: TokenUsage? = null
)

/**
 * Modèle de couleur extrait par l'agent.
 */
data class ExtractedColorItem(
    val nom: String,
    val hexCode: String
)

/**
 * Résultat de l'exécution d'une action par l'agent.
 */
sealed class SizeActionResult {
    abstract val tokenUsage: TokenUsage?

    data class PaletteExtracted(
        val paletteTitle: String,
        val explanation: String,
        val colors: List<ExtractedColorItem>,
        override val tokenUsage: TokenUsage? = null
    ) : SizeActionResult()

    data class AdjustmentProposed(
        val brightness: Float,
        val contrast: Float,
        val explanation: String,
        override val tokenUsage: TokenUsage? = null
    ) : SizeActionResult()

    data class ArtworkProposed(
        val artwork: ArtworkDisplayInfo,
        override val tokenUsage: TokenUsage? = null
    ) : SizeActionResult()

    data class GeneralMessage(
        val message: String,
        override val tokenUsage: TokenUsage? = null
    ) : SizeActionResult()

    data class Error(
        val errorMessage: String,
        override val tokenUsage: TokenUsage? = null
    ) : SizeActionResult()
}

/**
 * Service gérant les appels aux Assistants IA pour l'onglet Size :
 * 1. Conseiller Artistique (Advisor) : Analyse esthétique, critique, suggestions et affichage d'œuvres de maîtres.
 * 2. Opérateur d'Action (Action) : Actions concrètes (palette vers nuancier, réglages).
 */
class SizeAgentService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ColorAppAtelier/1.0 (michel.benay@gmail.com; Painting Atelier App)"

    /**
     * ASSISTANT 1 : Conseiller Artistique.
     * Envoie la question avec l'image optionnelle, reçoit une réponse d'analyse
     * accompagnée d'un tableau d'art spécifique préchargé en mémoire.
     */
    suspend fun consultAdvisor(
        imageBitmap: Bitmap?,
        userQuestion: String
    ): Result<AdvisorReply> = withContext(Dispatchers.IO) {
        try {
            val apiKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Clé GEMINI_API_KEY introuvable dans BuildConfig"))
            }

            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Clé GEMINI_API_KEY non configurée"))
            }

            val fullPrompt = """
                ${SizeAgentTools.ADVISOR_SYSTEM_INSTRUCTION}

                --- DEMANDE DE L'ARTISTE ---
                $userQuestion
            """.trimIndent()

            val partsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", fullPrompt)
                })
                if (imageBitmap != null) {
                    val imageBase64 = bitmapToBase64(imageBitmap)
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imageBase64)
                        })
                    })
                }
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 8192)
                })
            }

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var lastResponseCode = 0
            var responseString = ""
            val maxRetries = 3

            for (attempt in 1..maxRetries) {
                val response = httpClient.newCall(request).execute()
                lastResponseCode = response.code
                responseString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    break
                }

                if (lastResponseCode == 503 || lastResponseCode == 429 || lastResponseCode == 500) {
                    Log.w("SizeAgentService", "Conseiller - Tentative $attempt/$maxRetries échouée (HTTP $lastResponseCode). Nouvelle tentative...")
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(attempt * 1500L)
                        continue
                    }
                } else {
                    break
                }
            }

            if (lastResponseCode !in 200..299) {
                Log.e("SizeAgentService", "Erreur API ($lastResponseCode) : $responseString")
                val friendlyMsg = when (lastResponseCode) {
                    503 -> "Les serveurs Gemini sont momentanément saturés (Erreur 503). Veuillez réessayer dans quelques secondes."
                    429 -> "Limite de requêtes atteinte (Erreur 429). Veuillez patienter un court instant."
                    else -> "Erreur API ($lastResponseCode) : $responseString"
                }
                return@withContext Result.failure(Exception(friendlyMsg))
            }
            val jsonResponse = JSONObject(responseString)
            val tokenUsage = TokenUsageTracker.recordFromResponse(null, jsonResponse)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Aucune réponse générée par l'IA"))
            }

            val rawText = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val (cleanText, parsedArtwork) = extractArtworkFromText(rawText, userQuestion)

            if (parsedArtwork != null) {
                // Recherche dynamique et fidèle du tableau demandé (priorité Google Arts & Culture et musées d'art)
                val imgResult = fetchArtworkImageUrl(
                    searchTerm = parsedArtwork.searchTerm,
                    title = parsedArtwork.title,
                    artist = parsedArtwork.artist
                )
                parsedArtwork.imageUrl = imgResult.url
                parsedArtwork.sourceName = imgResult.sourceName

                // Pré-téléchargement direct du Bitmap en mémoire
                if (imgResult.url.isNotBlank()) {
                    parsedArtwork.bitmap = downloadDirectBitmap(imgResult.url)
                }
            }

            Result.success(AdvisorReply(textReply = cleanText, artwork = parsedArtwork, tokenUsage = tokenUsage))
        } catch (e: Exception) {
            Log.e("SizeAgentService", "Erreur lors de la consultation du conseiller", e)
            Result.failure(e)
        }
    }

    /**
     * ASSISTANT 2 : Opérateur d'Action.
     * Envoie l'image et l'instruction, extrait l'ordre JSON et le structure.
     */
    suspend fun executeAction(
        imageBitmap: Bitmap,
        userInstruction: String
    ): SizeActionResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                return@withContext SizeActionResult.Error("Clé GEMINI_API_KEY introuvable dans BuildConfig")
            }

            if (apiKey.isNullOrBlank()) {
                return@withContext SizeActionResult.Error("Clé GEMINI_API_KEY non configurée")
            }

            val imageBase64 = bitmapToBase64(imageBitmap)

            val fullPrompt = """
                ${SizeAgentTools.ACTION_SYSTEM_INSTRUCTION}

                --- DEMANDE DE L'UTILISATEUR ---
                $userInstruction
            """.trimIndent()

            val partsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", fullPrompt)
                })
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", imageBase64)
                    })
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 8192)
                })
            }

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var lastResponseCode = 0
            var responseString = ""
            val maxRetries = 3

            for (attempt in 1..maxRetries) {
                val response = httpClient.newCall(request).execute()
                lastResponseCode = response.code
                responseString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    break
                }

                if (lastResponseCode == 503 || lastResponseCode == 429 || lastResponseCode == 500) {
                    Log.w("SizeAgentService", "Action - Tentative $attempt/$maxRetries échouée (HTTP $lastResponseCode). Nouvelle tentative...")
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(attempt * 1500L)
                        continue
                    }
                } else {
                    break
                }
            }

            if (lastResponseCode !in 200..299) {
                Log.e("SizeAgentService", "Erreur API ($lastResponseCode) : $responseString")
                val friendlyMsg = when (lastResponseCode) {
                    503 -> "Les serveurs Gemini sont momentanément saturés (Erreur 503). Veuillez réessayer dans quelques secondes."
                    429 -> "Limite de requêtes atteinte (Erreur 429). Veuillez patienter un court instant."
                    else -> "Erreur API ($lastResponseCode) : $responseString"
                }
                return@withContext SizeActionResult.Error(friendlyMsg)
            }
            val jsonResponse = JSONObject(responseString)
            val tokenUsage = TokenUsageTracker.recordFromResponse(null, jsonResponse)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext SizeActionResult.Error("Aucune réponse générée par l'IA", tokenUsage)
            }

            val rawText = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            parseActionResult(rawText, tokenUsage)
        } catch (e: Exception) {
            Log.e("SizeAgentService", "Erreur lors de l'exécution de l'action", e)
            SizeActionResult.Error("Erreur : ${e.message ?: "inconnue"}")
        }
    }

    /**
     * Analyse le texte brut et le convertit en objet `SizeActionResult`.
     */
    private suspend fun parseActionResult(rawText: String, tokenUsage: TokenUsage? = null): SizeActionResult {
        val jsonPattern = Regex("""```json\s*(\{[\s\S]*?\})\s*```""")
        val match = jsonPattern.find(rawText)
        val jsonString = match?.groups?.get(1)?.value ?: run {
            val start = rawText.indexOf('{')
            val end = rawText.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) {
                rawText.substring(start, end + 1)
            } else null
        }

        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                val action = json.optString("action")

                when (action) {
                    "extraire_palette" -> {
                        val titre = json.optString("titre_palette", "Palette IA")
                        val explication = json.optString("explication", "")
                        val couleursArray = json.optJSONArray("couleurs")
                        val colorsList = mutableListOf<ExtractedColorItem>()
                        if (couleursArray != null) {
                            for (i in 0 until couleursArray.length()) {
                                val item = couleursArray.getJSONObject(i)
                                val nom = item.optString("nom", "Couleur $i")
                                var hex = item.optString("hex", "#000000")
                                if (!hex.startsWith("#")) hex = "#$hex"
                                colorsList.add(ExtractedColorItem(nom, hex))
                            }
                        }
                        return SizeActionResult.PaletteExtracted(titre, explication, colorsList, tokenUsage)
                    }
                    "ajuster_lumiere_contraste" -> {
                        val luminosite = json.optDouble("luminosite", 0.0).toFloat()
                        val contraste = json.optDouble("contraste", 0.0).toFloat()
                        val explication = json.optString("explication", "Réglages recommandés")
                        return SizeActionResult.AdjustmentProposed(luminosite, contraste, explication, tokenUsage)
                    }
                    "afficher_tableau" -> {
                        val titre = json.optString("titre", "Chef-d'œuvre")
                        val artiste = json.optString("artiste", "")
                        val annee = json.optString("annee", "")
                        val mouvement = json.optString("mouvement", "")
                        val explication = json.optString("explication", "")
                        val recherche = json.optString("recherche_image", "$artiste $titre")
                        val paletteArray = json.optJSONArray("couleurs_palette")
                        val colorsList = mutableListOf<ArtworkColorSwatch>()
                        if (paletteArray != null) {
                            for (i in 0 until paletteArray.length()) {
                                val item = paletteArray.getJSONObject(i)
                                val nom = item.optString("nom", "Couleur")
                                var hex = item.optString("hex", "#000000")
                                if (!hex.startsWith("#")) hex = "#$hex"
                                colorsList.add(ArtworkColorSwatch(nom, hex.uppercase()))
                            }
                        }
                        val imgResult = fetchArtworkImageUrl(recherche, titre, artiste)
                        val artwork = ArtworkDisplayInfo(
                            title = titre,
                            artist = artiste,
                            year = annee,
                            movement = mouvement,
                            explanation = explication,
                            searchTerm = recherche,
                            paletteColors = colorsList,
                            imageUrl = imgResult.url,
                            sourceName = imgResult.sourceName
                        )
                        if (imgResult.url.isNotBlank()) {
                            artwork.bitmap = downloadDirectBitmap(imgResult.url)
                        }
                        return SizeActionResult.ArtworkProposed(artwork, tokenUsage)
                    }
                }
            } catch (e: Exception) {
                Log.e("SizeAgentService", "Erreur lors du parsing JSON de l'action", e)
            }
        }

        return SizeActionResult.GeneralMessage(rawText, tokenUsage)
    }

    /**
     * Extrait les informations de l'œuvre depuis le texte de l'assistant (bloc artwork, json, ou heuristique).
     */
    private fun extractArtworkFromText(rawText: String, userQuestion: String = ""): Pair<String, ArtworkDisplayInfo?> {
        // 1. Chercher un bloc ```artwork ... ```
        val artworkBlockPattern = Regex("""```artwork\s*(\{[\s\S]*?\})\s*```""")
        val matchArtwork = artworkBlockPattern.find(rawText)
        if (matchArtwork != null) {
            val jsonStr = matchArtwork.groups[1]?.value ?: ""
            val parsed = parseArtworkJson(jsonStr)
            if (parsed != null) {
                val cleanText = rawText.replace(matchArtwork.value, "").trim()
                return Pair(cleanText, parsed)
            }
        }

        // 2. Chercher un bloc ```json ... ``` contenant "titre"
        val jsonBlockPattern = Regex("""```json\s*(\{[\s\S]*?\})\s*```""")
        val matchJson = jsonBlockPattern.find(rawText)
        if (matchJson != null) {
            val jsonStr = matchJson.groups[1]?.value ?: ""
            if (jsonStr.contains("\"titre\"") || jsonStr.contains("\"recherche_image\"")) {
                val parsed = parseArtworkJson(jsonStr)
                if (parsed != null) {
                    val cleanText = rawText.replace(matchJson.value, "").trim()
                    return Pair(cleanText, parsed)
                }
            }
        }

        // 3. Chercher un bloc JSON brut { ... } contenant "titre" et "artiste"
        val rawJsonPattern = Regex("""\{[\s\S]*?"titre"[\s\S]*?"artiste"[\s\S]*?\}""")
        val matchRaw = rawJsonPattern.find(rawText)
        if (matchRaw != null) {
            val parsed = parseArtworkJson(matchRaw.value)
            if (parsed != null) {
                val cleanText = rawText.replace(matchRaw.value, "").trim()
                return Pair(cleanText, parsed)
            }
        }

        // 4. Si la question demande explicitement un tableau mais que le bloc JSON n'a pas été généré
        val qLower = userQuestion.lowercase()
        val isPaintingRequest = qLower.contains("tableau") || qLower.contains("peinture") || 
                                qLower.contains("montre") || qLower.contains("affiche") ||
                                qLower.contains("œuvre") || qLower.contains("oeuvre")
        if (isPaintingRequest) {
            val extracted = extractArtworkFromHeuristics(rawText, userQuestion)
            if (extracted != null) {
                return Pair(rawText, extracted)
            }
        }

        return Pair(rawText, null)
    }

    private fun parseArtworkJson(jsonStr: String): ArtworkDisplayInfo? {
        return try {
            val json = JSONObject(jsonStr)
            val title = json.optString("titre", "Tableau")
            val artist = json.optString("artiste", "")
            val year = json.optString("annee", "")
            val movement = json.optString("mouvement", "")
            val searchTerm = json.optString("recherche_image", "$artist $title")
            val explanation = json.optString("explication", "")

            val colorsList = mutableListOf<ArtworkColorSwatch>()
            val colorsArray = json.optJSONArray("couleurs_palette")
            if (colorsArray != null) {
                for (i in 0 until colorsArray.length()) {
                    val item = colorsArray.getJSONObject(i)
                    val name = item.optString("nom", "Couleur ${i + 1}")
                    var hex = item.optString("hex", "#888888")
                    if (!hex.startsWith("#")) hex = "#$hex"
                    if (hex.length == 7) {
                        colorsList.add(ArtworkColorSwatch(name, hex.uppercase()))
                    }
                }
            }

            ArtworkDisplayInfo(
                title = title,
                artist = artist,
                year = year,
                movement = movement,
                explanation = explanation,
                searchTerm = searchTerm,
                paletteColors = colorsList
            )
        } catch (e: Exception) {
            Log.e("SizeAgentService", "Erreur parseArtworkJson", e)
            null
        }
    }

    private fun extractArtworkFromHeuristics(rawText: String, userQuestion: String): ArtworkDisplayInfo? {
        // Tenter d'extraire le titre entre guillemets de la question ou du texte
        val quotePattern = Regex("""[«"“]([^»"”]+)[»"”]""")
        val quoteMatch = quotePattern.find(userQuestion) ?: quotePattern.find(rawText)
        val extractedTitle = quoteMatch?.groups?.get(1)?.value?.trim() ?: run {
            // Nettoyage de la question pour en faire un titre de recherche
            userQuestion
                .replace("(?i)montre-moi".toRegex(), "")
                .replace("(?i)affiche".toRegex(), "")
                .replace("(?i)un tableau de".toRegex(), "")
                .replace("(?i)le tableau".toRegex(), "")
                .replace("(?i)peinture de".toRegex(), "")
                .trim()
        }

        if (extractedTitle.length in 3..60) {
            return ArtworkDisplayInfo(
                title = extractedTitle,
                artist = "",
                searchTerm = extractedTitle
            )
        }
        return null
    }

    /**
     * Recherche une reproduction haute résolution fidèle de l'œuvre demandée avec ordre de priorité strict :
     * 1. Google Arts & Culture (reproductions gigapixel / ultra-HD indexées sous Google Art Project)
     * 2. The Art Institute of Chicago Open Access (partenaire majeur de Google Arts & Culture, 100% toiles)
     * 3. The Metropolitan Museum of Art Open Access (peintures du musée)
     * 4. Wikimedia Commons (recherche spécifique d'œuvres avec filtre anti-portraits du peintre)
     * 5. Wikipédia FR et EN avec filtre anti-peintre et anti-biographie strict (exclut systématiquement
     *    la page du peintre pour ne renvoyer que la toile demandée)
     * 6. En ultime recours garanti : La Joconde (Louvre / C2RMF)
     */
    suspend fun fetchArtworkImageUrl(
        searchTerm: String,
        title: String,
        artist: String
    ): ArtworkImageResult = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()

        // 1. PRIORITÉ ABSOLUE : Google Arts & Culture (Google Art Project)
        if (cleanTitle.isNotBlank()) {
            searchGoogleArtsProject(cleanTitle, cleanArtist)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via Google Arts & Culture: $cleanTitle ($it)")
                return@withContext ArtworkImageResult(it, "Google Arts & Culture")
            }
        }

        // 2. The Art Institute of Chicago Open Access (Partenaire officiel Google Arts & Culture)
        if (cleanTitle.isNotBlank()) {
            searchArtInstituteOfChicago(cleanTitle, cleanArtist)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via The Art Institute of Chicago: $cleanTitle ($it)")
                return@withContext ArtworkImageResult(it, "Art Institute of Chicago")
            }
        }

        // 3. The Metropolitan Museum of Art Open Access
        val metQuery = if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else (searchTerm.ifBlank { cleanTitle })
        if (metQuery.isNotBlank()) {
            searchMetMuseum(metQuery)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via The Met Museum: $metQuery ($it)")
                return@withContext ArtworkImageResult(it, "Metropolitan Museum of Art")
            }
        }

        // 4. Wikimedia Commons (recherche ciblée sur l'œuvre avec filtre anti-portrait d'artiste)
        val commonsQueries = mutableListOf<String>()
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            commonsQueries.add("$cleanTitle $cleanArtist painting")
            commonsQueries.add("$cleanArtist $cleanTitle painting")
            commonsQueries.add("$cleanTitle ($cleanArtist)")
        }
        if (cleanTitle.isNotBlank()) {
            commonsQueries.add("$cleanTitle painting")
        }
        for (q in commonsQueries) {
            searchWikimediaCommons(q, cleanArtist)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via Wikimedia Commons: $q ($it)")
                return@withContext ArtworkImageResult(it, "Wikimedia Commons (Collection d'Art)")
            }
        }

        // 5. Wikipédia FR et EN avec filtre anti-peintre / anti-biographie strict
        val wikiQueries = mutableListOf<String>()
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            wikiQueries.add("$cleanTitle ($cleanArtist)")
            wikiQueries.add("$cleanTitle (tableau)")
            wikiQueries.add("$cleanTitle (peinture)")
            wikiQueries.add("$cleanTitle $cleanArtist tableau")
            wikiQueries.add("$cleanTitle $cleanArtist painting")
        }
        if (cleanTitle.isNotBlank()) {
            wikiQueries.add(cleanTitle)
        }
        if (searchTerm.isNotBlank() && searchTerm != cleanArtist) {
            wikiQueries.add(searchTerm)
        }

        // 5a. Wikipédia Français
        for (q in wikiQueries) {
            searchWikipediaPageImage("fr", q, cleanArtist)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via Wikipédia FR: $q ($it)")
                return@withContext ArtworkImageResult(it, "Wikipédia Beaux-Arts (FR)")
            }
        }

        // 5b. Wikipédia Anglais
        for (q in wikiQueries) {
            searchWikipediaPageImage("en", q, cleanArtist)?.let {
                Log.d("SizeAgentService", "Chef-d'œuvre trouvé via Wikipédia EN: $q ($it)")
                return@withContext ArtworkImageResult(it, "Wikipédia Beaux-Arts (EN)")
            }
        }

        // 6. En ultime recours garanti : La Joconde (format 800px optimisé du Louvre)
        val fallbackUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ec/Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg/800px-Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg?utm_source=commons.wikimedia.org&utm_campaign=api&utm_content=thumbnail"
        ArtworkImageResult(fallbackUrl, "Musée du Louvre (La Joconde)")
    }

    /**
     * Recherche les numérisations ultra-haute résolution de Google Arts & Culture
     * déposées sous le label "Google Art Project" sur Wikimedia Commons.
     */
    private fun searchGoogleArtsProject(title: String, artist: String): String? {
        val searchVariations = mutableListOf<String>()
        if (title.isNotBlank() && artist.isNotBlank()) {
            searchVariations.add("$title $artist Google Art Project")
            searchVariations.add("$artist $title Google Art Project")
            searchVariations.add("$title $artist Google Arts")
        }
        if (title.isNotBlank()) {
            searchVariations.add("$title Google Art Project")
        }

        for (query in searchVariations) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=$encoded&gsrlimit=6&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val pages = json.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val pageList = mutableListOf<JSONObject>()
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            pageList.add(pages.getJSONObject(keys.next()))
                        }
                        pageList.sortBy { it.optInt("index", 999) }

                        for (page in pageList) {
                            val fileTitle = page.optString("title", "")
                            if (isExcludedPageTitle(fileTitle, query)) continue
                            if (isArtistOrBiography(fileTitle, "", artist)) continue

                            val imgInfoArray = page.optJSONArray("imageinfo")
                            if (imgInfoArray != null && imgInfoArray.length() > 0) {
                                val info = imgInfoArray.getJSONObject(0)
                                val thumb = info.optString("thumburl")
                                val orig = info.optString("url")
                                val candidate = if (thumb.isNotBlank()) thumb else orig
                                if (isValidArtworkImage(candidate)) {
                                    return candidate
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SizeAgentService", "Erreur searchGoogleArtsProject pour: $query", e)
            }
        }
        return null
    }

    /**
     * Recherche dans l'API Open Access de l'Art Institute of Chicago (partenaire clé de Google Arts & Culture).
     */
    private fun searchArtInstituteOfChicago(title: String, artist: String): String? {
        val queries = mutableListOf<String>()
        if (title.isNotBlank() && artist.isNotBlank()) {
            queries.add("$title $artist")
        }
        if (title.isNotBlank()) {
            queries.add(title)
        }

        for (q in queries) {
            try {
                val encoded = java.net.URLEncoder.encode(q, "UTF-8")
                val searchUrl = "https://api.artic.edu/api/v1/artworks/search?q=$encoded&fields=id,title,artist_title,image_id&limit=4"
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val imageId = item.optString("image_id", "")
                            if (imageId.isNotBlank() && imageId != "null") {
                                return "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SizeAgentService", "Erreur searchArtInstituteOfChicago pour: $q", e)
            }
        }
        return null
    }

    /**
     * Vérifie si un titre ou une description Wikipédia correspond à la biographie ou au portrait
     * de l'artiste lui-même afin de NE JAMAIS afficher le peintre au lieu du tableau demandé.
     */
    private fun isArtistOrBiography(pageTitle: String, pageDescription: String, artist: String): Boolean {
        val lowerTitle = pageTitle.lowercase().trim()
        val lowerArtist = artist.lowercase().trim()
        val lowerDesc = pageDescription.lowercase().trim()

        // 1. Si le titre de la page est identique ou commence par le nom de l'artiste
        if (lowerArtist.isNotBlank()) {
            if (lowerTitle == lowerArtist) return true

            // Ex: "Vincent van Gogh (peintre)", "Claude Monet (artiste)", "Rembrandt (peintre)"
            if (lowerTitle.startsWith(lowerArtist) && (
                        lowerTitle.contains("peintre") ||
                        lowerTitle.contains("artiste") ||
                        lowerTitle.contains("biographie") ||
                        lowerTitle.contains("painter")
                    )) {
                return true
            }

            // Ex: si les mots du titre sont exactement les mots du nom de l'artiste sans titre d'œuvre
            val wordsTitle = lowerTitle.split(" ", "-", "_", "'", "’").filter { it.length > 2 }
            val wordsArtist = lowerArtist.split(" ", "-", "_", "'", "’").filter { it.length > 2 }
            if (wordsTitle.isNotEmpty() && wordsArtist.isNotEmpty() && wordsArtist.containsAll(wordsTitle)) {
                return true
            }

            // Si le fichier Commons est un portrait ou une photographie de l'artiste
            val isPortraitOfArtist = lowerTitle.contains("portrait of $lowerArtist") ||
                    lowerTitle.contains("portrait de $lowerArtist") ||
                    lowerTitle.contains("photo of $lowerArtist") ||
                    lowerTitle.contains("photograph of $lowerArtist") ||
                    lowerTitle.contains("photo de $lowerArtist")
            if (isPortraitOfArtist) {
                return true
            }
        }

        // 2. Si la description Wikidata (issue de Wikipédia) indique un métier d'artiste (personne humaine)
        if (lowerDesc.isNotBlank()) {
            val isArtworkDesc = lowerDesc.contains("tableau") || lowerDesc.contains("peinture") ||
                    lowerDesc.contains("toile") || lowerDesc.contains("painting") ||
                    lowerDesc.contains("oeuvre") || lowerDesc.contains("œuvre") ||
                    lowerDesc.contains("fresque") || lowerDesc.contains("fresco") ||
                    lowerDesc.contains("artwork") || lowerDesc.contains("gravure") ||
                    lowerDesc.contains("estampe") || lowerDesc.contains("dessin")

            if (!isArtworkDesc) {
                val personKeywords = listOf(
                    "peintre", "painter", "sculpteur", "sculptor", "artiste", "artist",
                    "dessinateur", "graveur", "illustrateur", "personnalité", "homme politique",
                    "biographie", "biography", "dutch painter", "french painter", "italian painter",
                    "spanish painter", "german painter", "flemish painter", "american painter",
                    "personne", "humain"
                )
                for (kw in personKeywords) {
                    if (lowerDesc.contains(kw)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun isExcludedPageTitle(pageTitle: String, userQuery: String): Boolean {
        val lowerTitle = pageTitle.lowercase()
        val lowerQuery = userQuery.lowercase()
        val buildingKeywords = listOf(
            "musée de ", "musée d'", "musée du ", "museum of ", "palais ", "palace", 
            "bâtiment", "building", "édifice", "château de ", "château d'", 
            "winter palace", "ermitage", "hermitage museum", "homonymie", "disambiguation",
            "liste de", "liste des", "list of", "signature", "plan_"
        )
        for (kw in buildingKeywords) {
            if (lowerTitle.contains(kw) && !lowerQuery.contains(kw)) {
                return true
            }
        }
        return false
    }

    private fun isValidArtworkImage(candidate: String): Boolean {
        val lower = candidate.lowercase()
        return candidate.startsWith("http") &&
                !lower.contains("icon") &&
                !lower.contains("logo") &&
                !lower.contains(".svg") &&
                !lower.contains("signature") &&
                !lower.contains("map_") &&
                !lower.contains("diagram")
    }

    private fun searchWikipediaPageImage(lang: String, query: String, artist: String): String? {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=6&prop=pageimages|description|info&piprop=original|thumbnail&pithumbsize=1000&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val pages = json.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val pageList = mutableListOf<JSONObject>()
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        pageList.add(pages.getJSONObject(keys.next()))
                    }
                    pageList.sortBy { it.optInt("index", 999) }

                    for (page in pageList) {
                        val pageTitle = page.optString("title", "")
                        val pageDescription = page.optString("description", "")

                        // Ignorer les édifices / musées
                        if (isExcludedPageTitle(pageTitle, query)) {
                            continue
                        }

                        // Ignorer STRICTEMENT les pages biographiques de l'artiste ou portraits
                        if (isArtistOrBiography(pageTitle, pageDescription, artist)) {
                            Log.d("SizeAgentService", "Page d'artiste rejetée: $pageTitle ($pageDescription)")
                            continue
                        }

                        val thumb = page.optJSONObject("thumbnail")?.optString("source")
                        val orig = page.optJSONObject("original")?.optString("source")
                        val candidate = when {
                            !thumb.isNullOrBlank() -> thumb
                            !orig.isNullOrBlank() -> orig
                            else -> null
                        }
                        if (candidate != null && isValidArtworkImage(candidate)) {
                            return candidate
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SizeAgentService", "Erreur searchWikipediaPageImage ($lang) pour: $query", e)
        }
        return null
    }

    private fun searchWikimediaCommons(query: String, artist: String): String? {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=$encoded&gsrlimit=8&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val pages = json.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val pageList = mutableListOf<JSONObject>()
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        pageList.add(pages.getJSONObject(keys.next()))
                    }
                    pageList.sortBy { it.optInt("index", 999) }

                    for (page in pageList) {
                        val fileTitle = page.optString("title", "").lowercase()
                        if (isExcludedPageTitle(fileTitle, query)) continue
                        if (isArtistOrBiography(fileTitle, "", artist)) continue

                        // Éliminer les photos de salles, de couloirs, de façades, d'édifices
                        val isRoomOrBuilding = fileTitle.contains("hall ") || fileTitle.contains("hall_") ||
                                fileTitle.contains("salle ") || fileTitle.contains("room ") ||
                                fileTitle.contains("building") || fileTitle.contains("facade") ||
                                fileTitle.contains("architecture") || fileTitle.contains("palace")

                        if (isRoomOrBuilding) {
                            continue
                        }

                        val imgInfoArray = page.optJSONArray("imageinfo")
                        if (imgInfoArray != null && imgInfoArray.length() > 0) {
                            val info = imgInfoArray.getJSONObject(0)
                            val thumb = info.optString("thumburl")
                            val orig = info.optString("url")
                            val candidate = if (thumb.isNotBlank()) thumb else orig
                            if (isValidArtworkImage(candidate)) {
                                return candidate
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SizeAgentService", "Erreur searchWikimediaCommons pour: $query", e)
        }
        return null
    }

    private fun searchMetMuseum(query: String): String? {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://collectionapi.metmuseum.org/public/collection/v1/search?q=$encoded&hasImages=true"
            val req = Request.Builder().url(searchUrl).header("User-Agent", BROWSER_USER_AGENT).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string() ?: "")
                val objectIds = json.optJSONArray("objectIDs")
                if (objectIds != null && objectIds.length() > 0) {
                    val limit = minOf(objectIds.length(), 4)
                    for (i in 0 until limit) {
                        val id = objectIds.getInt(i)
                        val oUrl = "https://collectionapi.metmuseum.org/public/collection/v1/objects/$id"
                        val oReq = Request.Builder().url(oUrl).header("User-Agent", BROWSER_USER_AGENT).build()
                        val oResp = httpClient.newCall(oReq).execute()
                        if (oResp.isSuccessful) {
                            val oJson = JSONObject(oResp.body?.string() ?: "")
                            val imgSmall = oJson.optString("primaryImageSmall")
                            val imgBig = oJson.optString("primaryImage")
                            val candidate = if (imgSmall.isNotBlank()) imgSmall else imgBig
                            if (candidate.isNotBlank() && candidate.startsWith("http")) {
                                return candidate
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SizeAgentService", "Erreur searchMetMuseum pour: $query", e)
        }
        return null
    }

    /**
     * Cas 2 : Télécharge directement le Bitmap via OkHttp avec les en-têtes navigateur
     * et le redimensionne de façon optimisée pour l'écran (max 1024px) avec échantillonnage mémoire.
     */
    fun downloadDirectBitmap(url: String, maxDim: Int = 1024): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: return null
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                val w = boundsOptions.outWidth
                val h = boundsOptions.outHeight
                var sampleSize = 1
                if (w > maxDim || h > maxDim) {
                    val halfW = w / 2
                    val halfH = h / 2
                    while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                if (decoded != null && (decoded.width > maxDim || decoded.height > maxDim)) {
                    val scaled = downscaleBitmap(decoded, maxDim)
                    if (scaled != decoded) decoded.recycle()
                    scaled
                } else {
                    decoded
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("SizeAgentService", "Erreur downloadDirectBitmap: $url", e)
            null
        }
    }

    /**
     * Télécharge le Bitmap haute résolution depuis son URL avec optimisation de taille.
     */
    suspend fun downloadBitmap(context: Context, imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        // Essai 1 : Téléchargement direct OkHttp avec User-Agent adapté et redimensionnement optimisé
        val directBitmap = downloadDirectBitmap(imageUrl, maxDim = 1024)
        if (directBitmap != null) {
            return@withContext directBitmap
        }

        // Essai 2 : Via Coil ImageLoader personnalisé
        try {
            val loader = ImageLoader.Builder(context)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val req = chain.request().newBuilder()
                                .header("User-Agent", BROWSER_USER_AGENT)
                                .build()
                            chain.proceed(req)
                        }
                        .build()
                }
                .build()

            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val bmp = drawable.bitmap
                    return@withContext if (bmp.width > 1024 || bmp.height > 1024) {
                        downscaleBitmap(bmp, 1024)
                    } else {
                        bmp
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SizeAgentService", "Erreur Coil downloadBitmap", e)
        }

        null
    }

    /**
     * Enregistre l'image dans la galerie de l'utilisateur (MediaStore).
     */
    suspend fun saveArtworkToGallery(context: Context, bitmap: Bitmap, title: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val filename = "Art_${cleanTitle}_${System.currentTimeMillis()}.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PeintureAtelier")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            true
        } catch (e: Exception) {
            Log.e("SizeAgentService", "Erreur enregistrement galerie", e)
            false
        }
    }

    /**
     * Cas 1 : Réduit intelligemment la taille d'un Bitmap pour l'analyse IA de Gemini Vision.
     * Une dimension maximale de 800px conserve la fidélité chromatique et structurelle
     * tout en réduisant la consommation de ~25 000 tokens à moins de 1 000 tokens par requête.
     */
    fun downscaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Cas 1 : Downscale à 800px max avant l'envoi vers Gemini Vision
        val scaled = downscaleBitmap(bitmap, 800)
        val stream = ByteArrayOutputStream()
        // Compression JPEG qualité 80% : très légère en mémoire et en tokens
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
