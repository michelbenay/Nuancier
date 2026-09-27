package com.example.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class FalseColorMode(val label: String, val description: String) {
    ORIGINAL("Naturelle", "Image originale avec corrections"),
    VALUES_THERMAL("Valeurs / Thermique", "Carte thermique des valeurs (luminosités)"),
    WARM_COOL("Chaud / Froid", "Carte des températures de couleur"),
    MASSES("Masses (4 niveaux)", "Simplification en grands aplats de composition"),
    SATURATION("Saturation", "Intensité et pureté pigmentaire")
}

/**
 * Ajuste la luminosité, le contraste et la teinte (rosace chromatique) d'un Bitmap via ColorMatrix.
 *
 * @param brightness Valeur de luminosité comprise entre -100f et +100f (0 = neutre)
 * @param contrast Valeur de contraste comprise entre -100f et +100f (0 = neutre)
 * @param tintHue Angle de teinte sur la rosace (0° à 360°)
 * @param tintSaturation Saturation / distance au centre de la rosace (0f à 1f)
 * @param tintIntensity Dosage global de l'effet (0f à 1f)
 */
fun adjustImageColor(
    bitmap: Bitmap,
    brightness: Float = 0f,
    contrast: Float = 0f,
    tintHue: Float = 0f,
    tintSaturation: Float = 0f,
    tintIntensity: Float = 0f
): Bitmap {
    val scale = if (contrast >= 0f) {
        1f + (contrast / 100f) * 1.5f
    } else {
        (100f + contrast) / 100f
    }

    val translate = 128f * (1f - scale) + brightness

    val cm = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val strength = (tintSaturation.coerceIn(0f, 1f) * tintIntensity.coerceIn(0f, 1f))
    if (strength > 0.005f) {
        val hsv = floatArrayOf((tintHue % 360f + 360f) % 360f, 1f, 1f)
        val colorInt = android.graphics.Color.HSVToColor(hsv)
        val rNorm = android.graphics.Color.red(colorInt) / 255f
        val gNorm = android.graphics.Color.green(colorInt) / 255f
        val bNorm = android.graphics.Color.blue(colorInt) / 255f

        // Multiplicateurs très doux et subtils adaptés à un glacis d'artiste (évite de saturer l'image)
        val rScale = 1f + (rNorm - 0.5f) * strength * 0.55f
        val gScale = 1f + (gNorm - 0.5f) * strength * 0.55f
        val bScale = 1f + (bNorm - 0.5f) * strength * 0.55f

        // Décalage léger des valeurs (offset) pour une coloration harmonieuse des gris sans brûler les ombres
        val rTrans = (rNorm - 0.5f) * strength * 12f
        val gTrans = (gNorm - 0.5f) * strength * 12f
        val bTrans = (bNorm - 0.5f) * strength * 12f

        val tintMatrix = ColorMatrix(
            floatArrayOf(
                rScale, 0f, 0f, 0f, rTrans,
                0f, gScale, 0f, 0f, gTrans,
                0f, 0f, bScale, 0f, bTrans,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(tintMatrix)
    }

    val ret = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(ret)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return ret
}

/**
 * Ajuste la luminosité et le contraste d'un Bitmap via ColorMatrix.
 *
 * @param brightness Valeur de luminosité comprise entre -100f et +100f (0 = neutre)
 * @param contrast Valeur de contraste comprise entre -100f et +100f (0 = neutre)
 */
fun adjustBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
    return adjustImageColor(bitmap, brightness, contrast, 0f, 0f, 0f)
}

/**
 * Retourne le nom artistique évocateur d'une teinte selon l'angle et la saturation de la rosace.
 */
fun getArtisticTintName(hue: Float, saturation: Float): String {
    if (saturation < 0.08f) return "Neutre / Naturel"
    val h = (hue % 360f + 360f) % 360f
    return when {
        h in 15f..45f -> "Ambre Chaud (Lumière d'or)"
        h in 45f..70f -> "Ocre Doré"
        h in 70f..150f -> "Vert Olive / Émeraude"
        h in 150f..195f -> "Cyan Glacial"
        h in 195f..250f -> "Bleu Outremer (Ombres fraîches)"
        h in 250f..290f -> "Indigo / Violet profond"
        h in 290f..335f -> "Magenta / Carmin"
        else -> "Rouge Vermillon"
    }
}

/**
 * Génère une carte de fausses couleurs selon le mode choisi avec dosage d'opacité.
 */
fun generateFalseColorBitmap(
    sourceBitmap: Bitmap,
    mode: FalseColorMode,
    falseColorOpacity: Float = 1.0f // 0f = originale, 1f = 100% fausses couleurs
): Bitmap {
    if (mode == FalseColorMode.ORIGINAL || falseColorOpacity <= 0f) {
        return sourceBitmap
    }

    val width = sourceBitmap.width
    val height = sourceBitmap.height
    val pixels = IntArray(width * height)
    sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val outPixels = IntArray(width * height)
    val hsv = FloatArray(3)

    val opacity = falseColorOpacity.coerceIn(0f, 1f)
    val invOpacity = 1f - opacity

    for (i in pixels.indices) {
        val origPixel = pixels[i]
        val a = (origPixel shr 24) and 0xFF
        val r = (origPixel shr 16) and 0xFF
        val g = (origPixel shr 8) and 0xFF
        val b = origPixel and 0xFF

        // Calcul de la luminance perceptuelle standard (Rec. 601 / 709)
        val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)

        val fcPixel: Int = when (mode) {
            FalseColorMode.ORIGINAL -> origPixel
            FalseColorMode.VALUES_THERMAL -> {
                // Palette thermique artistique : Noir -> Bleu nuit -> Cyan -> Vert -> Jaune -> Rouge -> Blanc
                when {
                    lum < 42 -> {
                        // Noir vers Bleu nuit
                        val t = lum / 42f
                        AndroidColor.rgb((10 * t).toInt(), (20 * t).toInt(), (120 + 80 * t).toInt())
                    }
                    lum < 85 -> {
                        // Bleu nuit vers Cyan
                        val t = (lum - 42) / 43f
                        AndroidColor.rgb(0, (20 + 190 * t).toInt(), (200 + 40 * t).toInt())
                    }
                    lum < 128 -> {
                        // Cyan vers Vert
                        val t = (lum - 85) / 43f
                        AndroidColor.rgb((0 + 40 * t).toInt(), 210, (240 * (1f - t)).toInt())
                    }
                    lum < 170 -> {
                        // Vert vers Jaune d'or
                        val t = (lum - 128) / 42f
                        AndroidColor.rgb((40 + 215 * t).toInt(), (210 + 35 * t).toInt(), 0)
                    }
                    lum < 215 -> {
                        // Jaune vers Rouge vif
                        val t = (lum - 170) / 45f
                        AndroidColor.rgb(255, (245 * (1f - t)).toInt(), (20 * t).toInt())
                    }
                    else -> {
                        // Rouge vif vers Blanc éclatant
                        val t = (lum - 215) / 40f
                        AndroidColor.rgb(255, (255 * t).toInt(), (255 * t).toInt())
                    }
                }
            }

            FalseColorMode.WARM_COOL -> {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0] // 0..360
                val sat = hsv[1] // 0..1

                if (sat < 0.12f) {
                    // Neutre / Gris (faible saturation)
                    val gray = (lum * 0.8f + 30).toInt().coerceIn(0, 255)
                    AndroidColor.rgb(gray, gray, gray)
                } else {
                    // Chaud : Teintes [315..360] et [0..85] (Magenta, Rouge, Orange, Jaune)
                    // Froid : Teintes [95..295] (Vert, Cyan, Bleu, Violet)
                    val isWarm = hue >= 315f || hue <= 85f
                    if (isWarm) {
                        // Tons chauds -> Dégradé Orange/Rouge/Ambre
                        val warmIntensity = (sat * 0.7f + 0.3f).coerceIn(0f, 1f)
                        val wr = (255 * warmIntensity).toInt()
                        val wg = (if (hue in 30f..85f) 180 * warmIntensity else 40 * warmIntensity).toInt()
                        val wb = (if (hue >= 315f) 80 * warmIntensity else 10).toInt()
                        AndroidColor.rgb(wr, wg, wb)
                    } else {
                        // Tons froids -> Dégradé Cyan/Bleu/Indigo
                        val coolIntensity = (sat * 0.7f + 0.3f).coerceIn(0f, 1f)
                        val cr = (if (hue >= 260f) 90 * coolIntensity else 15).toInt()
                        val cg = (if (hue in 95f..180f) 200 * coolIntensity else 100 * coolIntensity).toInt()
                        val cb = (255 * coolIntensity).toInt()
                        AndroidColor.rgb(cr, cg, cb)
                    }
                }
            }

            FalseColorMode.MASSES -> {
                // Quantification en 4 grandes masses (Notan / Valeurs clés)
                when {
                    lum < 64 -> AndroidColor.rgb(25, 30, 45)       // Masse d'ombres profondes
                    lum < 128 -> AndroidColor.rgb(75, 110, 140)    // Masse d'ombres moyennes
                    lum < 192 -> AndroidColor.rgb(220, 160, 60)    // Masse de demi-teintes éclairées
                    else -> AndroidColor.rgb(250, 240, 220)        // Masse de hautes lumières
                }
            }

            FalseColorMode.SATURATION -> {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1] // 0..1
                // Saturation élevée -> Magenta / Jaune fluo ; Faible -> Gris
                when {
                    sat < 0.20f -> {
                        val gVal = (lum * 0.7f + 40).toInt()
                        AndroidColor.rgb(gVal, gVal, gVal)
                    }
                    sat < 0.50f -> {
                        // Modéré -> Bleu sarcelle / Vert
                        val t = (sat - 0.20f) / 0.30f
                        AndroidColor.rgb(0, (140 + 80 * t).toInt(), (180 + 40 * t).toInt())
                    }
                    sat < 0.75f -> {
                        // Fort -> Orange vif
                        val t = (sat - 0.50f) / 0.25f
                        AndroidColor.rgb(255, (120 + 70 * t).toInt(), 0)
                    }
                    else -> {
                        // Extrême (pigment pur) -> Magenta fluo
                        AndroidColor.rgb(255, 0, 160)
                    }
                }
            }
        }

        // Fusion pondérée avec l'originale selon l'opacité
        if (opacity >= 0.99f) {
            outPixels[i] = (a shl 24) or (fcPixel and 0x00FFFFFF)
        } else {
            val fcR = (fcPixel shr 16) and 0xFF
            val fcG = (fcPixel shr 8) and 0xFF
            val fcB = fcPixel and 0xFF

            val blendR = (r * invOpacity + fcR * opacity).toInt().coerceIn(0, 255)
            val blendG = (g * invOpacity + fcG * opacity).toInt().coerceIn(0, 255)
            val blendB = (b * invOpacity + fcB * opacity).toInt().coerceIn(0, 255)

            outPixels[i] = (a shl 24) or (blendR shl 16) or (blendG shl 8) or blendB
        }
    }

    val resBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    resBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
    return resBitmap
}

