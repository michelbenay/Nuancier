package com.example.agent.calc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * ==================================================================================
 * FICHIER 1 / 3 : LES OUTILS DE L'AGENT (TOOLS) AVEC GESTION DE PARAMÈTRES
 * ==================================================================================
 * 
 * Dans l'architecture d'un Agent IA, un "Outil" (Tool) peut être :
 * 1. Sans paramètre (ex: effacer tout).
 * 2. AVEC PARAMÈTRES (ex: appliquer une couleur monochrome déduite par l'IA).
 * 
 * ICI, L'OUTIL POSSÈDE DEUX PARAMÈTRES :
 * - couleur_hex : le code couleur hexadécimal (ex: "#704214") déduit par Gemini.
 * - nom_teinte   : le libellé poétique ou artistique (ex: "Sépia vintage").
 * 
 * Gemini analyse la phrase en langage naturel (ex: "donne un style photo ancienne sépia"),
 * trouve la couleur hexadécimale correspondante, et transmet les paramètres dans son JSON d'ordre.
 */
object LayerAgentTools {

    /**
     * DÉFINITION DE L'OUTIL POUR L'IA (SYSTEM PROMPT)
     * 
     * C'est la notice fournie à Gemini à chaque requête.
     * Elle décrit le contrat, le nom de l'action, les paramètres attendus et le format JSON.
     */
    val TOOLS_SYSTEM_PROMPT = """
Tu es l'Agent IA d'Atelier spécialisé dans l'assistance artistique et la manipulation des calques de dessin.
Ton rôle est de comprendre la volonté esthétique de l'artiste et d'agir sur son calque actif.

OUTIL DISPONIBLE :

Nom de l'action : "appliquer_monochrome"
Description : 
Transforme le calque actif en une version monochrome (niveaux d'intensité lumineuse teintés).
Cet outil conserve les contrastes d'ombres et de lumières tout en appliquant une couleur dominante.

Paramètres de l'action :
- "couleur_hex" (chaîne au format "#RRGGBB") : la couleur que tu déduis selon la demande de l'artiste.
  Exemples de correspondances chromatiques à utiliser :
  • Noir et blanc classique / niveaux de gris neutres : "#808080"
  • Sépia / photographie ancienne / tons chauds vintage : "#704214"
  • Cyanotype / bleu nuit / bleu de Prusse / monochrome froid : "#1B3B6F"
  • Sanguine / ocre rouge / terre cuite / terracotta : "#8B2500"
  • Vert sauge / vert émeraude / sous-bois : "#2D5A27"
  • Violet mystique / lavande sombre : "#4A2545"
  • Pour toute autre nuance (ex: "ambiance coucher de soleil", "jaune moutarde"), choisis toi-même le code hexadécimal le plus pertinent.
- "nom_teinte" (chaîne) : nom français élégant de la teinte choisie (ex: "Sépia vintage", "Niveaux de gris neutre", "Bleu Cyanotype", "Sanguine").
- "cible" : "calque_actif"

FORMAT JSON STRICT À ÉMETTRE :
Si la demande de l'artiste concerne un effet monochrome, noir et blanc, sépia, cyanotype ou teinté, tu DOIS inclure ce bloc JSON à la fin de ta réponse :
```json
{
  "actions": [
    {
      "action": "appliquer_monochrome",
      "couleur_hex": "#704214",
      "nom_teinte": "Sépia vintage",
      "cible": "calque_actif"
    }
  ]
}
```

CONSIGNES DE RÉPONSE :
1. Rédige un court message bienveillant et artistique expliquant ton choix de teinte et l'effet produit sur les contrastes.
2. Termine par le bloc ```json ... ``` contenant les paramètres précis.
3. Si la demande ne correspond à aucun outil possible, explique poliment ce que tu sais faire.
""".trimIndent()

    /**
     * FONCTION GRAPHIQUE ANDROID (EXÉCUTION DU PARAMÈTRE EN KOTLIN)
     * 
     * Cette fonction reçoit le Bitmap source et la couleur choisie par Gemini.
     * 
     * Améliorations de traitement du signal (Luminosité & Contraste) :
     * 1. Normalisation de crête (Peak Normalization) :
     *    Empêche l'écrasement de l'image dans les tons sombres. Le canal le plus
     *    fort est ramené à 1.0 pour que les blancs restent éclatants (ex: ivoire chaud au lieu de marron).
     * 2. Gain de contraste (C) :
     *    Amplification dynamique des écarts autour du point pivot médian (128).
     * 3. Offset de luminosité (B) :
     *    Translation continue du signal pour déboucher les ombres.
     * 4. Préservation stricte du canal Alpha (transparence).
     */
    fun convertBitmapToMonochrome(
        sourceBitmap: Bitmap,
        tintColor: Int,
        contrast: Float = 1.15f,
        brightness: Float = 20f
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val rawR = Color.red(tintColor) / 255f
        val rawG = Color.green(tintColor) / 255f
        val rawB = Color.blue(tintColor) / 255f

        // 1. Normalisation crête : garantit que le blanc atteint la pleine échelle
        val maxChannel = maxOf(rawR, rawG, rawB).coerceAtLeast(0.1f)
        val normR = rawR / maxChannel
        val normG = rawG / maxChannel
        val normB = rawB / maxChannel

        // 2. Calcul du gain et de l'offset de contraste autour du point pivot 128
        val contrastOffset = (128f * (1f - contrast)) + brightness

        // 3. Matrice de transformation couleur 4x5
        val matrixArray = floatArrayOf(
            0.213f * normR * contrast, 0.715f * normR * contrast, 0.072f * normR * contrast, 0f, contrastOffset * normR, // Canal Rouge
            0.213f * normG * contrast, 0.715f * normG * contrast, 0.072f * normG * contrast, 0f, contrastOffset * normG, // Canal Vert
            0.213f * normB * contrast, 0.715f * normB * contrast, 0.072f * normB * contrast, 0f, contrastOffset * normB, // Canal Bleu
            0f,                        0f,                        0f,                        1f, 0f                    // Canal Alpha (Inchangé)
        )

        val colorMatrix = ColorMatrix(matrixArray)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        val canvas = Canvas(outputBitmap)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

        return outputBitmap
    }

    /**
     * Rétrocompatibilité : noir et blanc neutre (équivaut à un monochrome gris #808080).
     */
    fun convertBitmapToGrayscale(sourceBitmap: Bitmap): Bitmap {
        return convertBitmapToMonochrome(sourceBitmap, Color.rgb(128, 128, 128))
    }
}
