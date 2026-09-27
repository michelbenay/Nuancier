package com.example.utils

import kotlin.math.roundToInt

object ColorUtils {

    enum class PigmentOpacity(val label: String, val icon: String, val shortDesc: String) {
        TRANSPARENT("Transparent (Glacis)", "⬜", "Translucide, idéal pour glacis et superpositions"),
        SEMI_OPAQUE("Semi-opaque", "🌓", "Couvrance moyenne et veloutée"),
        OPAQUE("Opaque", "⬛", "Fort pouvoir couvrant, masque le fond")
    }

    data class PigmentInfo(
        val pigmentCode: String,
        val opacity: PigmentOpacity,
        val description: String = ""
    )

    data class GamutAnalysis(
        val isNearOrOutGamut: Boolean,
        val saturationPercent: Int,
        val warningText: String?,
        val badgeLabel: String?
    )

    // Liste prédéfinie des couleurs de peintres en Français
    val DEFAULT_ARTIST_COLORS = listOf(
        Pair("Blanc de Titane", "#FFFFFF"),
        Pair("Bleu Outremer", "#002FA7"),
        Pair("Rouge Cadmium", "#E30022"),
        Pair("Jaune Primaire", "#F6EB16"),
        Pair("Noir d'Ivoire", "#262626"),
        Pair("Bleu de Cobalt", "#0047AB"),
        Pair("Jaune d'Ocre", "#DFAF37"),
        Pair("Terre de Sienne Brûlée", "#8A3324"),
        Pair("Vert Émeraude", "#50C878"),
        Pair("Ocre Jaune", "#C68E17"),
        Pair("Rouge Carmin", "#960018"),
        Pair("Noir Neutre", "#484950"),
        Pair("Bleu Caeruleum", "#6B9BC3")
    )

    // Analyse du Gamut / Saturation pour détecter les couleurs réelles d'artiste qui dépassent l'espace sRVB d'un écran
    fun analyzeGamut(hexCode: String, colorName: String = ""): GamutAnalysis {
        val rgb = hexToRgb(hexCode)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (rgb[0] * 255).roundToInt(),
            (rgb[1] * 255).roundToInt(),
            (rgb[2] * 255).roundToInt(),
            hsv
        )
        val sat = hsv[1]
        val value = hsv[2]
        val satPct = (sat * 100).roundToInt()

        val normalizedName = colorName.lowercase()
        val isHighIntensePigmentName = normalizedName.contains("outremer") ||
                normalizedName.contains("cadmium") ||
                normalizedName.contains("phthalo") ||
                normalizedName.contains("quinacridone") ||
                normalizedName.contains("emeraude") ||
                normalizedName.contains("magenta") ||
                normalizedName.contains("dioxazine")

        val isOutGamut = (sat >= 0.78f && value >= 0.35f) || isHighIntensePigmentName

        return if (isOutGamut) {
            GamutAnalysis(
                isNearOrOutGamut = true,
                saturationPercent = satPct,
                warningText = "Pigment physique à haute intensité : sur votre toile ou palette, la couleur matérielle sera plus profonde et vibrante que la restitution limitée de cet écran sRVB.",
                badgeLabel = "💡 Limite sRVB (Rendu écran approximé)"
            )
        } else {
            GamutAnalysis(
                isNearOrOutGamut = false,
                saturationPercent = satPct,
                warningText = null,
                badgeLabel = null
            )
        }
    }

    data class StandardPigment(
        val code: String,
        val name: String,
        val refHex: String,
        val opacity: PigmentOpacity,
        val description: String
    )

    data class PigmentMatch(
        val code: String,
        val standardName: String,
        val opacity: PigmentOpacity,
        val matchPercentage: Int,
        val description: String,
        val refHexCode: String
    )

    val STANDARD_PIGMENTS = listOf(
        // BLANCS & GRIS (Sennelier)
        StandardPigment("PW6", "Blanc de Titane (Sennelier 116)", "#FFFFFF", PigmentOpacity.OPAQUE, "Blanc minéral très couvrant et lumineux"),
        StandardPigment("PW4", "Blanc de Zinc (Sennelier 112)", "#FEFEE2", PigmentOpacity.SEMI_OPAQUE, "Blanc délicat idéal pour glacis et mélanges pastel"),
        StandardPigment("PW6+PBk7", "Gris de Payne (Sennelier 703)", "#2F3A4C", PigmentOpacity.SEMI_OPAQUE, "Gris bleuté profond pour ombres et atmosphères"),
        
        // JAUNES & OCRES (Sennelier)
        StandardPigment("PY35", "Jaune de Cadmium Citron (Sennelier 501)", "#FFF700", PigmentOpacity.OPAQUE, "Jaune vif et très couvrant"),
        StandardPigment("PY35", "Jaune de Cadmium Clair (Sennelier 529)", "#FFE300", PigmentOpacity.OPAQUE, "Jaune chaud et intense d'origine minérale"),
        StandardPigment("PY37", "Jaune de Cadmium Moyen (Sennelier 539)", "#FFAE00", PigmentOpacity.OPAQUE, "Jaune d'or riche et opaque"),
        StandardPigment("PY37", "Jaune de Cadmium Foncé (Sennelier 543)", "#FF9A00", PigmentOpacity.OPAQUE, "Jaune-orange dense à fort pouvoir couvrant"),
        StandardPigment("PY153", "Jaune Indien (Sennelier 517)", "#E6A100", PigmentOpacity.TRANSPARENT, "Jaune chaud très transparent et lumineux pour glacis"),
        StandardPigment("PY40", "Auréoline (Sennelier 559)", "#F2CA00", PigmentOpacity.TRANSPARENT, "Jaune cobalt historique très transparent"),
        StandardPigment("PY184", "Jaune Primaire Sennelier (Sennelier 574)", "#F8E800", PigmentOpacity.SEMI_OPAQUE, "Jaune pur équilibré pour mélanges secondaires"),
        StandardPigment("PY150", "Jaune de Nickel Azo (Sennelier 511)", "#DAA520", PigmentOpacity.TRANSPARENT, "Jaune doré transparent aux nuances d'ambre"),
        StandardPigment("PY43", "Ocre Jaune Naturelle (Sennelier 568)", "#C68E17", PigmentOpacity.SEMI_OPAQUE, "Terre naturelle chaude et veloutée"),
        StandardPigment("PY42", "Ocre Jaune Synthétique (Sennelier 567)", "#C48113", PigmentOpacity.OPAQUE, "Terre martiale opaque et régulière"),
        StandardPigment("PY110", "Stil de Grain Jaune (Sennelier 561)", "#D48C00", PigmentOpacity.TRANSPARENT, "Teinte végétale chaude et très transparente"),

        // ORANGES (Sennelier)
        StandardPigment("PO20", "Orange de Cadmium (Sennelier 641)", "#FF5800", PigmentOpacity.OPAQUE, "Orange minéral éclatant et couvrant"),
        StandardPigment("PO62", "Orange de Benzimidazolone (Sennelier 645)", "#FF6A00", PigmentOpacity.SEMI_OPAQUE, "Orange moderne à haute tenue à la lumière"),
        StandardPigment("PO48", "Orange Quinacridone (Sennelier 640)", "#C04000", PigmentOpacity.TRANSPARENT, "Orange cuivré profond et très transparent"),
        StandardPigment("PO43", "Orange de Pérylène (Sennelier 643)", "#E03C00", PigmentOpacity.SEMI_OPAQUE, "Orange sombre et intense"),

        // ROUGES, MAGENTAS & ROSES (Sennelier)
        StandardPigment("PR108", "Rouge de Cadmium Clair (Sennelier 605)", "#E30022", PigmentOpacity.OPAQUE, "Rouge vif minéral très couvrant"),
        StandardPigment("PR108", "Rouge de Cadmium Moyen (Sennelier 611)", "#C60018", PigmentOpacity.OPAQUE, "Rouge de cadmium pur et profond"),
        StandardPigment("PR108", "Rouge de Cadmium Foncé (Sennelier 613)", "#9C0014", PigmentOpacity.OPAQUE, "Rouge grenat opaque et dense"),
        StandardPigment("PR254", "Rouge Hélios / Pyrrole (Sennelier 619)", "#D1001C", PigmentOpacity.SEMI_OPAQUE, "Rouge vif moderne d'une grande clarté"),
        StandardPigment("PR255", "Rouge Corail (Sennelier 621)", "#E3381B", PigmentOpacity.OPAQUE, "Rouge orangé chaleureux et lumineux"),
        StandardPigment("PR122", "Magenta Quinacridone (Sennelier 689)", "#CF025B", PigmentOpacity.TRANSPARENT, "Magenta primaire idéal pour violets éclatants"),
        StandardPigment("PR209", "Rouge Quinacridone (Sennelier 679)", "#D92B4B", PigmentOpacity.TRANSPARENT, "Rouge rosé transparent et vibrant"),
        StandardPigment("PR83", "Laque de Garance Foncé (Sennelier 690)", "#900020", PigmentOpacity.TRANSPARENT, "Laque historique carminée très transparente"),
        StandardPigment("PR176", "Carmine Sennelier (Sennelier 635)", "#9E002B", PigmentOpacity.TRANSPARENT, "Rouge profond violacé pour glacis sombres"),
        StandardPigment("PR101", "Rouge de Venise (Sennelier 623)", "#8B2500", PigmentOpacity.OPAQUE, "Terre rouge de fer opaque et chaleureuse"),
        StandardPigment("PR101", "Rouge de Mars (Sennelier 625)", "#7A1F0D", PigmentOpacity.OPAQUE, "Rouge minéral terreux très couvrant"),
        StandardPigment("PR101", "Caput Mortuum (Sennelier 919)", "#591A1D", PigmentOpacity.OPAQUE, "Brun-rouge violemment terreux et sombre"),
        StandardPigment("PR122+BV10", "Opéra Rose (Sennelier 659)", "#FF1493", PigmentOpacity.TRANSPARENT, "Rose néon fluorescent spectaculaire"),

        // VIOLETS & POURPRES (Sennelier)
        StandardPigment("PV23", "Violet Dioxazine (Sennelier 917)", "#3C0878", PigmentOpacity.TRANSPARENT, "Violet extrêmement sombre et concentré"),
        StandardPigment("PV14", "Violet de Cobalt (Sennelier 911)", "#601262", PigmentOpacity.SEMI_OPAQUE, "Violet minéral pur aux reflets roses"),
        StandardPigment("PV19", "Violet Quinacridone (Sennelier 915)", "#6B1135", PigmentOpacity.TRANSPARENT, "Violet rougeoyant d'une grande transparence"),
        StandardPigment("PV15", "Violet Outremer (Sennelier 913)", "#512888", PigmentOpacity.TRANSPARENT, "Violet bleuté granulant et transparent"),
        StandardPigment("PV16", "Violet de Manganèse (Sennelier 909)", "#5B205C", PigmentOpacity.SEMI_OPAQUE, "Violet doux et subtil pour ombres pastel"),

        // BLEUS (Sennelier)
        StandardPigment("PB29", "Bleu Outremer Clair (Sennelier 314)", "#0038A8", PigmentOpacity.TRANSPARENT, "Bleu historique vibrant aux reflets violacés"),
        StandardPigment("PB29", "Bleu Outremer Foncé (Sennelier 315)", "#002387", PigmentOpacity.TRANSPARENT, "Bleu Outremer très profond pour ombres froides"),
        StandardPigment("PB28", "Bleu de Cobalt (Sennelier 307)", "#0047AB", PigmentOpacity.SEMI_OPAQUE, "Bleu minéral velouté et très stable"),
        StandardPigment("PB35", "Bleu Céruléen (Sennelier 302)", "#2A52BE", PigmentOpacity.OPAQUE, "Bleu céleste opaque idéal pour ciels"),
        StandardPigment("PB36", "Bleu Céruléen Foncé (Sennelier 303)", "#005F9E", PigmentOpacity.OPAQUE, "Nuance turquoise profonde et opaque"),
        StandardPigment("PB27", "Bleu de Prusse (Sennelier 318)", "#003153", PigmentOpacity.TRANSPARENT, "Bleu sombre intense à forte teinte verte"),
        StandardPigment("PB15:3", "Bleu Phthalo Nuance Verte (Sennelier 326)", "#000F89", PigmentOpacity.TRANSPARENT, "Bleu synthétique ultra-puissant et transparent"),
        StandardPigment("PB15:1", "Bleu Phthalo Nuance Rouge (Sennelier 325)", "#0B1C72", PigmentOpacity.TRANSPARENT, "Bleu cyan intense pour mélanges violacés"),
        StandardPigment("PB60", "Bleu Indanthrène (Sennelier 395)", "#031138", PigmentOpacity.TRANSPARENT, "Bleu de nuit très foncé et résistant"),
        StandardPigment("PB16", "Bleu Turquoise (Sennelier 341)", "#006D77", PigmentOpacity.TRANSPARENT, "Turquoise pur très transparent"),
        StandardPigment("PB28", "Bleu de Sèvres (Sennelier 309)", "#004F98", PigmentOpacity.SEMI_OPAQUE, "Bleu cobalt profond et élégant"),

        // VERTS (Sennelier)
        StandardPigment("PG7", "Vert Phthalo Nuance Bleutée (Sennelier 805)", "#00A86B", PigmentOpacity.TRANSPARENT, "Vert froid très transparent et puissant"),
        StandardPigment("PG36", "Vert Phthalo Nuance Jaune (Sennelier 807)", "#008B45", PigmentOpacity.TRANSPARENT, "Vert émeraude chaud et translucide"),
        StandardPigment("PG18", "Vert Émeraude / Viridian (Sennelier 837)", "#007A5E", PigmentOpacity.TRANSPARENT, "Vert céladon historique transparent"),
        StandardPigment("PG36+PY83", "Vert de Vessie (Sennelier 817)", "#4B6B00", PigmentOpacity.TRANSPARENT, "Vert végétal chaud idéal pour paysages"),
        StandardPigment("PG17", "Vert d'Oxyde de Chrome (Sennelier 815)", "#2E582E", PigmentOpacity.OPAQUE, "Vert mat extrêmement couvrant et terne"),
        StandardPigment("PG23", "Terre Verte (Sennelier 813)", "#556B2F", PigmentOpacity.TRANSPARENT, "Vert naturel très doux pour carnations"),
        StandardPigment("PG19", "Vert de Cobalt (Sennelier 809)", "#238A6A", PigmentOpacity.SEMI_OPAQUE, "Vert pastel minéral doux"),
        StandardPigment("PG8", "Vert Hooker (Sennelier 803)", "#1B4D3E", PigmentOpacity.TRANSPARENT, "Vert forêt foncé et naturel"),
        StandardPigment("PG50", "Vert Turquoise de Cobalt (Sennelier 825)", "#00868B", PigmentOpacity.SEMI_OPAQUE, "Vert turquoise lumineux et frais"),

        // TERRES & BRUNS (Sennelier)
        StandardPigment("PY43", "Terre de Sienne Naturelle (Sennelier 211)", "#A06B22", PigmentOpacity.TRANSPARENT, "Terre dorée transparente pour paysages"),
        StandardPigment("PBr7", "Terre de Sienne Brûlée (Sennelier 213)", "#8A3324", PigmentOpacity.TRANSPARENT, "Brun rouille chaud et transparent"),
        StandardPigment("PBr7", "Terre d'Ombre Naturelle (Sennelier 205)", "#635147", PigmentOpacity.TRANSPARENT, "Brun terreux froid aux reflets verdâtres"),
        StandardPigment("PBr7", "Terre d'Ombre Brûlée (Sennelier 202)", "#4A2C11", PigmentOpacity.TRANSPARENT, "Brun foncé chaud très utilisé en ombrage"),
        StandardPigment("NBr8", "Brun Van Dyck (Sennelier 208)", "#3B270C", PigmentOpacity.TRANSPARENT, "Brun bistre très foncé d'origine naturelle"),
        StandardPigment("PBr7+PBk9", "Sépia (Sennelier 440)", "#322214", PigmentOpacity.TRANSPARENT, "Brun noir velouté historique"),
        StandardPigment("PBr25", "Brun Quinacridone (Sennelier 217)", "#5C2216", PigmentOpacity.TRANSPARENT, "Brun rougeoyant très transparent"),
        StandardPigment("PBk9", "Noir d'Ivoire (Sennelier 755)", "#1C1C1C", PigmentOpacity.SEMI_OPAQUE, "Noir velouté d'origine d'os calciné"),
        StandardPigment("PBk11", "Noir de Mars (Sennelier 753)", "#0B0B0B", PigmentOpacity.OPAQUE, "Noir de fer minéral très dense et neutre")
    )

    fun getClosestStandardPigment(hexCode: String, colorName: String = ""): PigmentMatch {
        val normName = colorName.lowercase().trim()
            .replace("[éèêë]".toRegex(), "e")
            .replace("[ûü]".toRegex(), "u")
            .replace("[îï]".toRegex(), "i")
            .replace("[àâä]".toRegex(), "a")
            .replace("[ôö]".toRegex(), "o")

        // Match direct par le nom si un terme explicite est renseigné
        if (normName.isNotBlank()) {
            for (pigment in STANDARD_PIGMENTS) {
                val pNorm = pigment.name.lowercase()
                    .replace("[éèêë]".toRegex(), "e")
                    .replace("[ûü]".toRegex(), "u")
                    .replace("[îï]".toRegex(), "i")
                    .replace("[àâä]".toRegex(), "a")
                    .replace("[ôö]".toRegex(), "o")
                if (normName.contains(pNorm) ||
                    (normName.contains("outremer") && pigment.code.startsWith("PB29")) ||
                    (normName.contains("titane") && pigment.code.startsWith("PW6")) ||
                    (normName.contains("cadmium") && normName.contains("rouge") && pigment.code.startsWith("PR108")) ||
                    (normName.contains("cadmium") && normName.contains("jaune") && pigment.code.startsWith("PY35")) ||
                    (normName.contains("cobalt") && pigment.code.startsWith("PB28")) ||
                    (normName.contains("sienne") && pigment.code.startsWith("PBr7")) ||
                    (normName.contains("ocre") && pigment.code.startsWith("PY43"))
                ) {
                    return PigmentMatch(
                        code = pigment.code,
                        standardName = pigment.name,
                        opacity = pigment.opacity,
                        matchPercentage = 100,
                        description = pigment.description,
                        refHexCode = pigment.refHex
                    )
                }
            }
        }

        // Distance chromatique pondérée visuelle dans l'espace RVB (Red=0.3, Green=0.59, Blue=0.11)
        val targetRgb = hexToRgb(hexCode)
        var bestPigment = STANDARD_PIGMENTS[0]
        var minDistance = Float.MAX_VALUE

        for (pigment in STANDARD_PIGMENTS) {
            val refRgb = hexToRgb(pigment.refHex)
            val dr = (targetRgb[0] - refRgb[0]) * 0.30f
            val dg = (targetRgb[1] - refRgb[1]) * 0.59f
            val db = (targetRgb[2] - refRgb[2]) * 0.11f
            val dist = Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
            if (dist < minDistance) {
                minDistance = dist
                bestPigment = pigment
            }
        }

        val maxPossibleDist = 0.67f // sqrt(0.3^2 + 0.59^2 + 0.11^2)
        val similarityPct = ((1f - (minDistance / maxPossibleDist).coerceIn(0f, 1f)) * 100f).roundToInt()

        return PigmentMatch(
            code = bestPigment.code,
            standardName = bestPigment.name,
            opacity = bestPigment.opacity,
            matchPercentage = similarityPct,
            description = bestPigment.description,
            refHexCode = bestPigment.refHex
        )
    }

    // Récupération des informations de pigment (C.I. Code Index & Opacité physique)
    fun getPigmentInfo(colorName: String, hexCode: String): PigmentInfo {
        val norm = colorName.trim().lowercase()
            .replace("[éèêë]".toRegex(), "e")
            .replace("[ûü]".toRegex(), "u")
            .replace("[îï]".toRegex(), "i")
            .replace("[àâä]".toRegex(), "a")
            .replace("[ôö]".toRegex(), "o")

        return when {
            norm.contains("titane") || norm.contains("titanium") -> PigmentInfo("PW6", PigmentOpacity.OPAQUE, "Blanc de Titane pur, très haut pouvoir couvrant")
            norm.contains("zinc") -> PigmentInfo("PW4", PigmentOpacity.SEMI_OPAQUE, "Blanc de Zinc, délicat pour glacis clairs")
            norm.contains("outremer") || norm.contains("ultramarine") -> PigmentInfo("PB29", PigmentOpacity.TRANSPARENT, "Bleu Outremer historique, transparent pour glacis profonds")
            norm.contains("cobalt") -> PigmentInfo("PB28", PigmentOpacity.SEMI_OPAQUE, "Bleu de Cobalt minéral, très velouté et stable")
            norm.contains("caeruleum") || norm.contains("ceruleum") || norm.contains("ceruleen") || norm.contains("cerulean") -> PigmentInfo("PB35", PigmentOpacity.OPAQUE, "Bleu Caeruleum minéral dense, opaque pour ciels")
            norm.contains("prusse") || norm.contains("prussian") -> PigmentInfo("PB27", PigmentOpacity.TRANSPARENT, "Bleu de Prusse, haut pouvoir teintant transparent")
            norm.contains("rouge cadmium") -> PigmentInfo("PR108", PigmentOpacity.OPAQUE, "Rouge de Cadmium minéral, opaque et éclatant")
            norm.contains("jaune cadmium") -> PigmentInfo("PY35", PigmentOpacity.OPAQUE, "Jaune de Cadmium dense et très couvrant")
            norm.contains("orange cadmium") -> PigmentInfo("PO20", PigmentOpacity.OPAQUE, "Orange de Cadmium opaque et très lumineux")
            norm.contains("carmin") || norm.contains("alizarine") || norm.contains("garance") -> PigmentInfo("PR83", PigmentOpacity.TRANSPARENT, "Laque transparente idéale pour glacis magenta")
            norm.contains("quinacridone") -> PigmentInfo("PR122", PigmentOpacity.TRANSPARENT, "Magenta Quinacridone vibrant et transparent")
            norm.contains("phthalo") && norm.contains("bleu") -> PigmentInfo("PB15:3", PigmentOpacity.TRANSPARENT, "Bleu Phthalo intense et hautement transparent")
            norm.contains("phthalo") && norm.contains("vert") -> PigmentInfo("PG7", PigmentOpacity.TRANSPARENT, "Vert Phthalo froid et translucide")
            norm.contains("emeraude") -> PigmentInfo("PG18", PigmentOpacity.TRANSPARENT, "Vert Émeraude lumineux et transparent")
            norm.contains("vessie") -> PigmentInfo("PG36", PigmentOpacity.TRANSPARENT, "Vert de Vessie naturel pour paysages")
            norm.contains("ocre") -> PigmentInfo("PY43", PigmentOpacity.SEMI_OPAQUE, "Ocre jaune minérale, terre douce semi-opaque")
            norm.contains("sienne") && norm.contains("brulee") -> PigmentInfo("PBr7", PigmentOpacity.TRANSPARENT, "Terre de Sienne Brûlée, chaude et transparente")
            norm.contains("sienne") -> PigmentInfo("PY43", PigmentOpacity.SEMI_OPAQUE, "Terre de Sienne Naturelle semi-opaque")
            norm.contains("ombre") -> PigmentInfo("PBr7", PigmentOpacity.TRANSPARENT, "Terre d'Ombre transparente pour ombrages")
            norm.contains("ivoire") -> PigmentInfo("PBk9", PigmentOpacity.SEMI_OPAQUE, "Noir d'Ivoire velouté")
            norm.contains("mars") -> PigmentInfo("PBk11", PigmentOpacity.OPAQUE, "Noir de Mars minéral, opaque et neutre")
            norm.contains("noir neutre") || norm.contains("noir") -> PigmentInfo("PBk7", PigmentOpacity.OPAQUE, "Noir Neutre minéral opaque (Noir de carbone)")
            norm.contains("jaune primaire") -> PigmentInfo("PY184", PigmentOpacity.SEMI_OPAQUE, "Jaune primaire pur semi-opaque")
            else -> {
                // Recherche automatique par similarité de couleur hexadécimale
                val match = getClosestStandardPigment(hexCode, colorName)
                PigmentInfo(match.code, match.opacity, "${match.standardName} (${match.matchPercentage}% de correspondance)")
            }
        }
    }

    // Profil global d'opacité d'un mélange de pigments
    fun getMixtureOpacityProfile(components: List<RecipeComponent>): String {
        if (components.isEmpty()) return "Non déterminé"
        var opaqueParts = 0
        var transparentParts = 0
        var totalParts = 0
        for (c in components) {
            val parts = c.parts
            totalParts += parts
            if (c.opacityLabel.contains("Opaque") && !c.opacityLabel.contains("Semi")) {
                opaqueParts += parts
            } else if (c.opacityLabel.contains("Transparent")) {
                transparentParts += parts
            }
        }
        val opaqueRatio = opaqueParts.toFloat() / totalParts
        val transparentRatio = transparentParts.toFloat() / totalParts

        return when {
            opaqueRatio >= 0.5f -> "⬛ Dominante Opaque (Haut pouvoir couvrant)"
            transparentRatio >= 0.5f -> "⬜ Dominante Translucide (Idéal pour glacis)"
            else -> "🌓 Semi-Opaque (Equilibre entre couvrance et transparence)"
        }
    }


    val FRENCH_COLOR_DICTIONARY = mapOf(
        "blanc de titane" to "#FFFFFF",
        "blanc de zinc" to "#FEFEE2",
        "blanc" to "#FFFFFF",
        "bleu outremer" to "#002FA7",
        "bleu de cobalt" to "#0047AB",
        "bleu ceruleen" to "#2A52BE",
        "bleu de prusse" to "#003153",
        "bleu" to "#0000FF",
        "rouge cadmium" to "#E30022",
        "rouge carmin" to "#960018",
        "laque de garance" to "#A81C07",
        "alizarine" to "#E32636",
        "rouge" to "#FF0000",
        "jaune de chrome" to "#FFAF00",
        "jaune cadmium" to "#FFF600",
        "jaune d'ocre" to "#DFAF37",
        "ocre jaune" to "#DFAF37",
        "jaune primaire" to "#F6EB16",
        "jaune" to "#FFFF00",
        "vert de vessie" to "#123524",
        "vert emeraude" to "#50C878",
        "vert" to "#00FF00",
        "terre de sienne brulee" to "#8A3324",
        "terre de sienne" to "#E7A854",
        "terre d'ombre brulee" to "#583730",
        "noir d'ivoire" to "#262626",
        "noir" to "#000000",
        "magenta" to "#FF00FF",
        "cyan" to "#00FFFF",
        "orange" to "#FFA500",
        "violet" to "#8F00FF",
        "rose" to "#FFC0CB",
        "marron" to "#582900",
        "gris" to "#808080"
    )

    fun guessColorHex(name: String): String? {
        val normalized = name.trim().lowercase()
            .replace("[éèêë]".toRegex(), "e")
            .replace("[ûü]".toRegex(), "u")
            .replace("[îï]".toRegex(), "i")
            .replace("[àâä]".toRegex(), "a")
            .replace("[ôö]".toRegex(), "o")
            .replace("[ç]".toRegex(), "c")
        
        // Correspondance exacte
        FRENCH_COLOR_DICTIONARY[normalized]?.let { return it }
        
        // Correspondance partielle
        for ((key, value) in FRENCH_COLOR_DICTIONARY) {
            if (normalized.contains(key) || key.contains(normalized)) {
                return value
            }
        }
        return null
    }

    fun hexToRgb(hex: String): FloatArray {
        val cleanHex = hex.replace("#", "")
        return try {
            val r = cleanHex.substring(0, 2).toInt(16) / 255f
            val g = cleanHex.substring(2, 4).toInt(16) / 255f
            val b = cleanHex.substring(4, 6).toInt(16) / 255f
            floatArrayOf(r, g, b)
        } catch (e: Exception) {
            floatArrayOf(0.5f, 0.5f, 0.5f)
        }
    }

    fun rgbToHex(r: Float, g: Float, b: Float): String {
        val ri = (r.coerceIn(0f, 1f) * 255).roundToInt()
        val gi = (g.coerceIn(0f, 1f) * 255).roundToInt()
        val bi = (b.coerceIn(0f, 1f) * 255).roundToInt()
        return String.format("#%02X%02X%02X", ri, gi, bi)
    }

    // Mélange soustractif RYB (Rouge, Jaune, Bleu) vers RGB via interpolation trilinéaire
    fun rybSubtractiveToRgb(r: Float, y: Float, b: Float): FloatArray {
        // Les 8 coins du cube RYB exprimés en RGB
        val c000 = floatArrayOf(1.0f, 1.0f, 1.0f) // Blanc (0,0,0) - Pas de pigment
        val c100 = floatArrayOf(1.0f, 0.0f, 0.0f) // Rouge
        val c010 = floatArrayOf(1.0f, 1.0f, 0.0f) // Jaune
        val c001 = floatArrayOf(0.0f, 0.0f, 1.0f) // Bleu
        val c110 = floatArrayOf(1.0f, 0.5f, 0.0f) // Orange (Rouge + Jaune)
        val c101 = floatArrayOf(0.5f, 0.0f, 0.5f) // Violet (Rouge + Bleu)
        val c011 = floatArrayOf(0.0f, 0.6f, 0.2f) // Vert (Jaune + Bleu)
        val c111 = floatArrayOf(0.2f, 0.15f, 0.1f) // Noir/Gris sombre (Rouge + Jaune + Bleu)

        val rgb = FloatArray(3)
        for (i in 0..2) {
            val r000 = c000[i]
            val r100 = c100[i]
            val r010 = c010[i]
            val r001 = c001[i]
            val r110 = c110[i]
            val r101 = c101[i]
            val r011 = c011[i]
            val r111 = c111[i]

            // Interpolation selon la composante r (Rouge)
            val r00 = r000 * (1f - r) + r100 * r
            val r01 = r001 * (1f - r) + r101 * r
            val r10 = r010 * (1f - r) + r110 * r
            val r11 = r011 * (1f - r) + r111 * r

            // Interpolation selon la composante y (Jaune)
            val r0 = r00 * (1f - y) + r10 * y
            val r1 = r01 * (1f - y) + r11 * y

            // Interpolation selon la composante b (Bleu)
            rgb[i] = r0 * (1f - b) + r1 * b
        }
        return rgb
    }

    // Convertit RGB vers RYB soustractif pour aligner les sliders
    fun rgbToRybSubtractive(r: Float, g: Float, b: Float): FloatArray {
        val ryb = rgbToRyb(r, g, b)
        val w = minOf(ryb[0], ryb[1], ryb[2])
        val m = maxOf(ryb[0], ryb[1], ryb[2])
        val k = 1f - m
        
        val rSub = (ryb[0] - w + k).coerceIn(0f, 1f)
        val ySub = (ryb[1] - w + k).coerceIn(0f, 1f)
        val bSub = (ryb[2] - w + k).coerceIn(0f, 1f)
        return floatArrayOf(rSub, ySub, bSub)
    }

    // Convertit RGB vers RYB (Rouge, Jaune, Bleu)
    private fun rgbToRyb(r: Float, g: Float, b: Float): FloatArray {
        val w = minOf(r, g, b)
        val r1 = r - w
        val g1 = g - w
        val b1 = b - w

        val mg = maxOf(r1, g1, b1)

        val y = minOf(r1, g1)
        val r2 = r1 - y
        val g2 = g1 - y

        val b2 = b1 + g2

        val ryb_r = r2
        val ryb_y = (y + g2) / 2f
        val ryb_b = b2

        val n = maxOf(ryb_r, ryb_y, ryb_b)
        if (n > 0 && mg > 0) {
            val factor = mg / n
            return floatArrayOf(ryb_r * factor + w, ryb_y * factor + w, ryb_b * factor + w)
        }
        return floatArrayOf(w, w, w)
    }

    // Convertit RYB (Rouge, Jaune, Bleu) vers RGB
    private fun rybToRgb(r: Float, y: Float, b: Float): FloatArray {
        val w = minOf(r, y, b)
        val r1 = r - w
        val y1 = y - w
        val b1 = b - w

        val my = maxOf(r1, y1, b1)

        val g = minOf(y1, b1)
        val y2 = y1 - g
        val b2 = b1 - g

        val rgb_r = r1 + y2
        val rgb_g = g + y2
        val rgb_b = b2

        val n = maxOf(rgb_r, rgb_g, rgb_b)
        if (n > 0 && my > 0) {
            val factor = my / n
            return floatArrayOf(rgb_r * factor + w, rgb_g * factor + w, rgb_b * factor + w)
        }
        return floatArrayOf(w, w, w)
    }

    // Mélange plusieurs couleurs selon leurs proportions respectives
    fun blendColors(colorsAndWeights: List<Pair<String, Float>>): String {
        if (colorsAndWeights.isEmpty()) return "#FFFFFF"
        if (colorsAndWeights.size == 1) return colorsAndWeights.first().first

        var totalWeight = colorsAndWeights.sumOf { it.second.toDouble() }.toFloat()
        if (totalWeight <= 0f) {
            totalWeight = colorsAndWeights.size.toFloat()
        }

        var blendedR = 0f
        var blendedY = 0f
        var blendedB = 0f

        for ((hex, weight) in colorsAndWeights) {
            val rgb = hexToRgb(hex)
            val ryb = rgbToRyb(rgb[0], rgb[1], rgb[2])
            
            val normWeight = weight / totalWeight
            blendedR += ryb[0] * normWeight
            blendedY += ryb[1] * normWeight
            blendedB += ryb[2] * normWeight
        }

        val finalRgb = rybToRgb(blendedR, blendedY, blendedB)
        return rgbToHex(finalRgb[0], finalRgb[1], finalRgb[2])
    }

    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        var h = 0f
        if (delta > 0f) {
            if (max == r) {
                h = (g - b) / delta
                if (h < 0f) h += 6f
            } else if (max == g) {
                h = (b - r) / delta + 2f
            } else {
                h = (r - g) / delta + 4f
            }
            h *= 60f
        }

        val s = if (max == 0f) 0f else delta / max
        val v = max

        return floatArrayOf(h, s, v)
    }

    fun hexToRyb(hex: String): FloatArray {
        val rgb = hexToRgb(hex)
        return rgbToRybSubtractive(rgb[0], rgb[1], rgb[2])
    }

    fun areColorsSimilar(hex1: String, hex2: String, name1: String = "", name2: String = ""): Boolean {
        val rgb1 = hexToRgb(hex1)
        val rgb2 = hexToRgb(hex2)

        val dr = (rgb1[0] - rgb2[0]) * 255f
        val dg = (rgb1[1] - rgb2[1]) * 255f
        val db = (rgb1[2] - rgb2[2]) * 255f

        val distance = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())

        // Tolerance visuelle RGB (distance < 30)
        if (distance < 30.0) return true

        // Même nom de couleur et distance RGB < 50
        if (name1.isNotBlank() && name2.isNotBlank() && name1.equals(name2, ignoreCase = true) && distance < 50.0) {
            return true
        }

        return false
    }

    fun getClosestColorName(hex: String): String {
        val rgb = hexToRgb(hex)
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]
        
        var bestMatchName: String? = null
        var minDistance = Double.MAX_VALUE
        
        for ((name, dictHex) in FRENCH_COLOR_DICTIONARY) {
            val dictRgb = hexToRgb(dictHex)
            val dr = r - dictRgb[0]
            val dg = g - dictRgb[1]
            val db = b - dictRgb[2]
            val dist = dr * dr + dg * dg + db * db
            if (dist < minDistance) {
                minDistance = dist.toDouble()
                bestMatchName = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
        
        if (minDistance < 0.008 && bestMatchName != null) {
            return bestMatchName
        }
        
        val hsv = rgbToHsv(r, g, b)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        
        val lightness = when {
            v < 0.2f -> "Sombre"
            v > 0.75f && s < 0.35f && s >= 0.06f -> "Clair"
            v > 0.9f && s < 0.06f -> ""
            s < 0.15f -> "Grisé"
            s > 0.75f -> "Vif"
            else -> ""
        }
        
        val baseName = when {
            v < 0.12f -> "Noir"
            v > 0.9f && s < 0.08f -> "Blanc"
            s < 0.12f -> "Gris"
            h in 0f..20f -> if (v < 0.5f && s > 0.3f) "Terre / Marron" else "Rouge"
            h in 20f..45f -> if (s < 0.45f && v < 0.75f) "Ocre / Brun" else "Orange"
            h in 45f..70f -> "Jaune"
            h in 70f..165f -> "Vert"
            h in 165f..260f -> "Bleu"
            h in 260f..320f -> "Violet"
            else -> "Rose"
        }
        
        return if (lightness.isNotEmpty() && baseName != "Noir" && baseName != "Blanc") {
            "$baseName $lightness"
        } else {
            baseName
        }
    }

    fun getColorSortScore(hex: String, name: String): Double {
        val rgb = hexToRgb(hex)
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]
        
        val hsv = rgbToHsv(r, g, b)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        
        val nameLower = name.lowercase()
        
        // 1. White
        val hasWhiteKeyword = nameLower.contains("blanc") || nameLower.contains("white") || nameLower.contains("clair") || nameLower.contains("neige") || nameLower.contains("albâtre") || nameLower.contains("albatre")
        if (hasWhiteKeyword || (s < 0.15f && v > 0.85f) || (r > 0.93f && g > 0.93f && b > 0.93f)) {
            return 10000.0 + v
        }
        
        // 5. Black
        val hasBlackKeyword = nameLower.contains("noir") || nameLower.contains("black") || nameLower.contains("sombre") || nameLower.contains("charbon") || nameLower.contains("foncé") || nameLower.contains("fonce") || nameLower.contains("anthracite")
        if (hasBlackKeyword || v < 0.18f || (r < 0.18f && g < 0.18f && b < 0.18f)) {
            return 2000.0 - (v * 100.0)
        }
        
        // Convert to RYB subtractive to get Yellow, Red, Blue components
        val rybSub = rgbToRybSubtractive(r, g, b)
        val rSub = rybSub[0]
        val ySub = rybSub[1]
        val bSub = rybSub[2]
        
        // Keyword heuristics to aid classification
        val hasYellowOrGreenKeyword = nameLower.contains("jaune") || nameLower.contains("yellow") || nameLower.contains("ocre") || nameLower.contains("or") || nameLower.contains("citron") ||
                nameLower.contains("vert") || nameLower.contains("green") || nameLower.contains("olive") || nameLower.contains("emeraude") || nameLower.contains("vessie") || 
                nameLower.contains("menthe") || nameLower.contains("lime") || nameLower.contains("jade") || nameLower.contains("pistache") || nameLower.contains("sable") || 
                nameLower.contains("anis") || nameLower.contains("sapin") || nameLower.contains("pelouse") || nameLower.contains("bambou") || nameLower.contains("tilleul") || 
                nameLower.contains("mousse") || nameLower.contains("prairie") || nameLower.contains("sauge")
                
        val hasRedOrOrangeKeyword = nameLower.contains("rouge") || nameLower.contains("red") || nameLower.contains("magenta") || nameLower.contains("rose") || nameLower.contains("pink") || 
                nameLower.contains("carmin") || nameLower.contains("pourpre") || nameLower.contains("sienne") || nameLower.contains("orange") || nameLower.contains("vermillon") || 
                nameLower.contains("corail") || nameLower.contains("bordeaux") || nameLower.contains("mauve") || nameLower.contains("saumon") || nameLower.contains("brique") || 
                nameLower.contains("grenat") || nameLower.contains("tomate") || nameLower.contains("framboise") || nameLower.contains("cerise") || nameLower.contains("fraise") || 
                nameLower.contains("abricot") || nameLower.contains("mandarine") || nameLower.contains("peche") || nameLower.contains("pêche") || nameLower.contains("terracotta")
                
        val hasBlueOrVioletKeyword = nameLower.contains("bleu") || nameLower.contains("blue") || nameLower.contains("outremer") || nameLower.contains("cyan") || nameLower.contains("phtalo") || 
                nameLower.contains("cobalt") || nameLower.contains("indigo") || nameLower.contains("turquoise") || nameLower.contains("violet") || nameLower.contains("celeste") || 
                nameLower.contains("céleste") || nameLower.contains("azur") || nameLower.contains("marine") || nameLower.contains("ardoise") || nameLower.contains("prune") || 
                nameLower.contains("lavande") || nameLower.contains("lilas") || nameLower.contains("myrtille") || nameLower.contains("ocean") || nameLower.contains("océan")
        
        // Categorize into Yellow, Red, Blue using Hue, RYB Subtractive, and keywords
        var category = 3 // default: Red
        if (hasYellowOrGreenKeyword) {
            category = 2
        } else if (hasRedOrOrangeKeyword) {
            category = 3
        } else if (hasBlueOrVioletKeyword) {
            category = 4
        } else {
            if (rSub < 0.12f && ySub > 0.05f) {
                category = 2 // No red, contains yellow -> Yellows / Greens (commencer par le plus de jaune)
            } else if (h >= 22f && h < 75f) {
                // Orange / Yellow
                category = if (ySub >= rSub) 2 else 3
            } else if (h >= 75f && h < 165f) {
                // Green: Yellow vs Blue pigment (si autant de bleu que de jaune, classer dans les jaunes)
                // Augmenter la marge à 0.015f pour gérer les imprécisions d'arrondi de la quantification Hexa 8-bit
                category = if (ySub >= bSub - 0.015f) 2 else 4
            } else if (h >= 165f && h < 255f) {
                category = 4 // Blue / Cyan
            } else if (h >= 255f && h < 330f) {
                // Violet / Magenta / Red-Violet: check if red pigment is stronger than blue
                category = if (rSub > bSub) 3 else 4
            } else {
                category = 3 // Red / Pink / Magenta
            }
        }
        
        return when (category) {
            2 -> 8000.0 + (ySub * 1000.0) - (rSub * 100.0) - (bSub * 100.0)
            3 -> 6000.0 + (rSub * 1000.0) - (ySub * 100.0) - (bSub * 100.0)
            4 -> 4000.0 + (bSub * 1000.0) - (rSub * 100.0) - (ySub * 100.0)
            else -> 6000.0
        }
    }

    data class RecipeComponent(
        val colorName: String,
        val hexCode: String,
        val parts: Int,
        val percentage: Int,
        val pigmentCode: String = "",
        val opacityLabel: String = "",
        val opacityIcon: String = ""
    )

    data class ColorDecompositionResult(
        val targetHex: String,
        val simulatedHex: String,
        val matchScorePercent: Int,
        val recipeItems: List<RecipeComponent>
    )

    fun findBestDecomposition(
        targetHex: String,
        availableBaseColors: List<Pair<String, String>>
    ): ColorDecompositionResult? {
        if (availableBaseColors.isEmpty()) return null

        val targetRgb = hexToRgb(targetHex)

        var bestSimulatedHex = "#FFFFFF"
        var bestDistance = Double.MAX_VALUE
        var bestRecipe = listOf<Pair<Pair<String, String>, Int>>()

        fun evaluateCandidate(candidateMix: List<Pair<Pair<String, String>, Int>>) {
            val colorsAndWeights = candidateMix.map { Pair(it.first.second, it.second.toFloat()) }
            val blendedHex = blendColors(colorsAndWeights)
            val blendedRgb = hexToRgb(blendedHex)

            val dr = (blendedRgb[0] - targetRgb[0]) * 255f
            val dg = (blendedRgb[1] - targetRgb[1]) * 255f
            val db = (blendedRgb[2] - targetRgb[2]) * 255f

            // Pondération de la perception visuelle humaine des couleurs
            val dist = Math.sqrt((2.0 * dr * dr + 4.0 * dg * dg + 3.0 * db * db))
            val colorCountPenalty = (candidateMix.size - 1) * 1.5

            val totalDist = dist + colorCountPenalty

            if (totalDist < bestDistance) {
                bestDistance = totalDist
                bestSimulatedHex = blendedHex
                bestRecipe = candidateMix
            }
        }

        // 1. Couleurs simples (100% d'une seule couleur de base)
        for (c in availableBaseColors) {
            evaluateCandidate(listOf(Pair(c, 1)))
        }

        // 2. Mélanges de 2 couleurs avec différents ratios de parts
        val ratioPairs = listOf(
            Pair(1, 1), Pair(2, 1), Pair(1, 2), Pair(3, 1), Pair(1, 3),
            Pair(4, 1), Pair(1, 4), Pair(3, 2), Pair(2, 3), Pair(5, 1),
            Pair(1, 5), Pair(4, 3), Pair(3, 4), Pair(5, 2), Pair(2, 5),
            Pair(6, 1), Pair(1, 6), Pair(7, 1), Pair(1, 7), Pair(8, 1), Pair(1, 8),
            Pair(9, 1), Pair(1, 9), Pair(10, 1), Pair(1, 10)
        )

        for (i in availableBaseColors.indices) {
            for (j in i + 1 until availableBaseColors.size) {
                val c1 = availableBaseColors[i]
                val c2 = availableBaseColors[j]
                for ((w1, w2) in ratioPairs) {
                    evaluateCandidate(listOf(Pair(c1, w1), Pair(c2, w2)))
                }
            }
        }

        // 3. Mélanges de 3 couleurs avec ratios clés
        if (availableBaseColors.size >= 3) {
            val tripletRatios = listOf(
                Triple(1, 1, 1), Triple(2, 1, 1), Triple(1, 2, 1), Triple(1, 1, 2),
                Triple(2, 2, 1), Triple(2, 1, 2), Triple(1, 2, 2), Triple(3, 1, 1),
                Triple(1, 3, 1), Triple(1, 1, 3), Triple(3, 2, 1), Triple(2, 3, 1),
                Triple(1, 2, 3), Triple(4, 2, 1), Triple(1, 2, 4), Triple(4, 1, 1),
                Triple(1, 4, 1), Triple(1, 1, 4), Triple(5, 2, 1), Triple(5, 1, 2),
                Triple(2, 5, 1), Triple(1, 2, 5), Triple(3, 3, 1), Triple(3, 1, 3),
                Triple(1, 3, 3), Triple(5, 1, 1), Triple(1, 5, 1), Triple(1, 1, 5)
            )

            val n = availableBaseColors.size
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    for (k in j + 1 until n) {
                        val c1 = availableBaseColors[i]
                        val c2 = availableBaseColors[j]
                        val c3 = availableBaseColors[k]
                        for ((w1, w2, w3) in tripletRatios) {
                            evaluateCandidate(listOf(Pair(c1, w1), Pair(c2, w2), Pair(c3, w3)))
                        }
                    }
                }
            }
        }

        if (bestRecipe.isEmpty()) return null

        val totalParts = bestRecipe.sumOf { it.second }
        val recipeComponents = bestRecipe.map { (colorInfo, parts) ->
            val pct = ((parts.toFloat() / totalParts) * 100).roundToInt()
            val pigment = getPigmentInfo(colorInfo.first, colorInfo.second)
            RecipeComponent(
                colorName = colorInfo.first,
                hexCode = colorInfo.second,
                parts = parts,
                percentage = pct,
                pigmentCode = pigment.pigmentCode,
                opacityLabel = pigment.opacity.label,
                opacityIcon = pigment.opacity.icon
            )
        }

        val rawScore = (100.0 - (bestDistance / 2.5)).toInt()
        val matchScore = rawScore.coerceIn(0, 100)

        return ColorDecompositionResult(
            targetHex = targetHex,
            simulatedHex = bestSimulatedHex,
            matchScorePercent = matchScore,
            recipeItems = recipeComponents
        )
    }
}
