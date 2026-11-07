package com.unera.social.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * Animated Background Categories for text posts.
 */
enum class AnimatedBgCategory(val label: String, val emoji: String) {
    REALISTIC("Realistic", "🌟"),
    EXISTING("Classic", "🎨"),
    FLOWERS("Flowers", "🌸"),
    CHARACTERS_3D("3D Characters", "🧸"),
    ROMANTIC("Romantic", "❤️"),
    MAGICAL("Magical", "✨"),
    CELEBRATION("Celebration", "🎉"),
    NATURE("Nature", "🌿"),
    ENVIRONMENTS("Environments", "🌊")
}

/**
 * TextPostBackgroundTemplate - Defines a background option (classic gradient or high-fidelity animated scene).
 */
data class TextPostBackgroundTemplate(
    val id: String,
    val label: String,
    val category: AnimatedBgCategory,
    val baseColors: List<Color>,
    val styleString: String,
    val iconEmoji: String,
    val isAnimated: Boolean = false,
    val defaultTextColor: Color = Color.White,
    val defaultAlignment: TextAlign = TextAlign.Center
)

/**
 * Complete collection of text post backgrounds, preserving all 7 existing options
 * alongside the newly expanded premium animated backgrounds.
 */
val ALL_TEXT_POST_BACKGROUNDS: List<TextPostBackgroundTemplate> = listOf(
    // ==========================================
    // 1. EXISTING CLASSIC BACKGROUNDS (PRESERVED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "none",
        label = "None",
        category = AnimatedBgCategory.EXISTING,
        baseColors = emptyList(),
        styleString = "",
        iconEmoji = "⊘",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "red",
        label = "Ruby Red",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFFFF0057), Color(0xFFE64C4C)),
        styleString = "linear-gradient(45deg, #FF0057, #E64C4C)",
        iconEmoji = "🔴",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "blue",
        label = "Ocean Blue",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
        styleString = "linear-gradient(45deg, #00C6FF, #0072FF)",
        iconEmoji = "🔵",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "green",
        label = "Mint Green",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFFA8FF78), Color(0xFF78FFD6)),
        styleString = "linear-gradient(45deg, #a8ff78, #78ffd6)",
        iconEmoji = "🟢",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "purple",
        label = "Sunset Purple",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFFE65C00), Color(0xFFF9D423)),
        styleString = "linear-gradient(45deg, #e65c00, #F9D423)",
        iconEmoji = "🟣",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "dark",
        label = "Midnight Dark",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFF434343), Color(0xFF000000)),
        styleString = "linear-gradient(to right, #434343 0%, black 100%)",
        iconEmoji = "⚫",
        isAnimated = false
    ),
    TextPostBackgroundTemplate(
        id = "fire",
        label = "Warm Fire",
        category = AnimatedBgCategory.EXISTING,
        baseColors = listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
        styleString = "linear-gradient(120deg, #f6d365 0%, #fda085 100%)",
        iconEmoji = "🔥",
        isAnimated = false
    ),

    // ==========================================
    // 2. FLOWERS AND NATURE (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:blooming_rose",
        label = "Blooming Rose",
        category = AnimatedBgCategory.FLOWERS,
        baseColors = listOf(Color(0xFF2A0815), Color(0xFF5B0E2D), Color(0xFFBE123C)),
        styleString = "animated:blooming_rose",
        iconEmoji = "🌹",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:cherry_blossom",
        label = "Cherry Blossom",
        category = AnimatedBgCategory.FLOWERS,
        baseColors = listOf(Color(0xFF1F1235), Color(0xFF38184C), Color(0xFFF472B6)),
        styleString = "animated:cherry_blossom",
        iconEmoji = "🌸",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:sunflower_breeze",
        label = "Sunflower Sunburst",
        category = AnimatedBgCategory.FLOWERS,
        baseColors = listOf(Color(0xFF422006), Color(0xFF78350F), Color(0xFFF59E0B)),
        styleString = "animated:sunflower_breeze",
        iconEmoji = "🌻",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:wind_flowers",
        label = "Wildflowers in Wind",
        category = AnimatedBgCategory.FLOWERS,
        baseColors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF818CF8)),
        styleString = "animated:wind_flowers",
        iconEmoji = "💐",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:golden_petals",
        label = "Falling Gold Petals",
        category = AnimatedBgCategory.FLOWERS,
        baseColors = listOf(Color(0xFF14532D), Color(0xFF166534), Color(0xFFFACC15)),
        styleString = "animated:golden_petals",
        iconEmoji = "🏵️",
        isAnimated = true
    ),

    // ==========================================
    // 3. NATURE & GARDEN (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:garden_butterflies",
        label = "Butterfly Garden",
        category = AnimatedBgCategory.NATURE,
        baseColors = listOf(Color(0xFF062C1E), Color(0xFF0F4D35), Color(0xFF34D399)),
        styleString = "animated:garden_butterflies",
        iconEmoji = "🦋",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:forest_fireflies",
        label = "Enchanted Fireflies",
        category = AnimatedBgCategory.NATURE,
        baseColors = listOf(Color(0xFF022C22), Color(0xFF064E3B), Color(0xFFA7F3D0)),
        styleString = "animated:forest_fireflies",
        iconEmoji = "🌲",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:waterfall_mist",
        label = "Waterfall Mist",
        category = AnimatedBgCategory.NATURE,
        baseColors = listOf(Color(0xFF082F49), Color(0xFF0C4A6E), Color(0xFF38BDF8)),
        styleString = "animated:waterfall_mist",
        iconEmoji = "🏞️",
        isAnimated = true
    ),

    // ==========================================
    // 4. 3D CHARACTERS (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:dancing_bear",
        label = "Dancing Teddy Bear",
        category = AnimatedBgCategory.CHARACTERS_3D,
        baseColors = listOf(Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFFA855F7)),
        styleString = "animated:dancing_bear",
        iconEmoji = "🧸",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:dancing_panda",
        label = "Groovy Panda",
        category = AnimatedBgCategory.CHARACTERS_3D,
        baseColors = listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF10B981)),
        styleString = "animated:dancing_panda",
        iconEmoji = "🐼",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:bouncing_bunny",
        label = "Bouncing Bunny",
        category = AnimatedBgCategory.CHARACTERS_3D,
        baseColors = listOf(Color(0xFF3B0764), Color(0xFF581C87), Color(0xFFC084FC)),
        styleString = "animated:bouncing_bunny",
        iconEmoji = "🐰",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:kawaii_kitty",
        label = "Waving Kawaii Kitty",
        category = AnimatedBgCategory.CHARACTERS_3D,
        baseColors = listOf(Color(0xFF4C0519), Color(0xFF831843), Color(0xFFFB7185)),
        styleString = "animated:kawaii_kitty",
        iconEmoji = "🐱",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:fantasy_fairy",
        label = "Magic Fairy",
        category = AnimatedBgCategory.CHARACTERS_3D,
        baseColors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF818CF8)),
        styleString = "animated:fantasy_fairy",
        iconEmoji = "🧚",
        isAnimated = true
    ),

    // ==========================================
    // 5. ROMANTIC AND EMOTIONAL (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:floating_hearts",
        label = "Floating 3D Hearts",
        category = AnimatedBgCategory.ROMANTIC,
        baseColors = listOf(Color(0xFF3B0724), Color(0xFF701A45), Color(0xFFF43F5E)),
        styleString = "animated:floating_hearts",
        iconEmoji = "❤️",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:beating_heart",
        label = "Beating Heart",
        category = AnimatedBgCategory.ROMANTIC,
        baseColors = listOf(Color(0xFF450A0A), Color(0xFF7F1D1D), Color(0xFFEF4444)),
        styleString = "animated:beating_heart",
        iconEmoji = "💓",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:rose_petals_love",
        label = "Rose Petals Love",
        category = AnimatedBgCategory.ROMANTIC,
        baseColors = listOf(Color(0xFF310B1E), Color(0xFF65163D), Color(0xFFE11D48)),
        styleString = "animated:rose_petals_love",
        iconEmoji = "🌹",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:romantic_glow",
        label = "Romantic Starry Glow",
        category = AnimatedBgCategory.ROMANTIC,
        baseColors = listOf(Color(0xFF280521), Color(0xFF4A0E4E), Color(0xFFD946EF)),
        styleString = "animated:romantic_glow",
        iconEmoji = "💫",
        isAnimated = true
    ),

    // ==========================================
    // 6. MAGICAL AND FANTASY (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:aurora_borealis",
        label = "Aurora Borealis",
        category = AnimatedBgCategory.MAGICAL,
        baseColors = listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF10B981), Color(0xFF8B5CF6)),
        styleString = "animated:aurora_borealis",
        iconEmoji = "🌌",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:cosmic_stars",
        label = "Cosmic Galaxy Stars",
        category = AnimatedBgCategory.MAGICAL,
        baseColors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF38BDF8)),
        styleString = "animated:cosmic_stars",
        iconEmoji = "🌠",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:magical_orbs",
        label = "Magical Floating Orbs",
        category = AnimatedBgCategory.MAGICAL,
        baseColors = listOf(Color(0xFF1E1B4B), Color(0xFF311042), Color(0xFFA855F7)),
        styleString = "animated:magical_orbs",
        iconEmoji = "🔮",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:fantasy_castle",
        label = "Dreamy Fantasy Spire",
        category = AnimatedBgCategory.MAGICAL,
        baseColors = listOf(Color(0xFF180A2E), Color(0xFF31104E), Color(0xFFC084FC)),
        styleString = "animated:fantasy_castle",
        iconEmoji = "🏰",
        isAnimated = true
    ),

    // ==========================================
    // 7. CELEBRATION (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:confetti_fiesta",
        label = "Confetti Fiesta",
        category = AnimatedBgCategory.CELEBRATION,
        baseColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFF59E0B), Color(0xFFEC4899)),
        styleString = "animated:confetti_fiesta",
        iconEmoji = "🎊",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:rising_balloons",
        label = "Celebration Balloons",
        category = AnimatedBgCategory.CELEBRATION,
        baseColors = listOf(Color(0xFF172554), Color(0xFF1E3A8A), Color(0xFFEF4444), Color(0xFFF59E0B)),
        styleString = "animated:rising_balloons",
        iconEmoji = "🎈",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:fireworks_night",
        label = "Midnight Fireworks",
        category = AnimatedBgCategory.CELEBRATION,
        baseColors = listOf(Color(0xFF030712), Color(0xFF0B0F19), Color(0xFFF43F5E), Color(0xFF38BDF8)),
        styleString = "animated:fireworks_night",
        iconEmoji = "🎆",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:birthday_sparkles",
        label = "Golden Sparkles",
        category = AnimatedBgCategory.CELEBRATION,
        baseColors = listOf(Color(0xFF1A1305), Color(0xFF2E2209), Color(0xFFFACC15)),
        styleString = "animated:birthday_sparkles",
        iconEmoji = "🎂",
        isAnimated = true
    ),

    // ==========================================
    // 8. OTHER ENVIRONMENTS (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:ocean_fish",
        label = "Ocean Waves & Fish",
        category = AnimatedBgCategory.ENVIRONMENTS,
        baseColors = listOf(Color(0xFF022C22), Color(0xFF064E3B), Color(0xFF0E7490)),
        styleString = "animated:ocean_fish",
        iconEmoji = "🐠",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:falling_snow",
        label = "Falling Snow",
        category = AnimatedBgCategory.ENVIRONMENTS,
        baseColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFE2E8F0)),
        styleString = "animated:falling_snow",
        iconEmoji = "❄️",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:gentle_rain",
        label = "Gentle Rain",
        category = AnimatedBgCategory.ENVIRONMENTS,
        baseColors = listOf(Color(0xFF090D16), Color(0xFF131D2D), Color(0xFF60A5FA)),
        styleString = "animated:gentle_rain",
        iconEmoji = "🌧️",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:sunset_clouds",
        label = "Sunset Clouds",
        category = AnimatedBgCategory.ENVIRONMENTS,
        baseColors = listOf(Color(0xFF431407), Color(0xFF7C2D12), Color(0xFFD97706)),
        styleString = "animated:sunset_clouds",
        iconEmoji = "🌅",
        isAnimated = true
    ),

    // ==========================================
    // 9. REALISTIC ENVIRONMENTS & 3D (ANIMATED)
    // ==========================================
    TextPostBackgroundTemplate(
        id = "animated:realistic_magical_garden",
        label = "Magical Garden",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF080D35), Color(0xFF45246F), Color(0xFF087E78)),
        styleString = "animated:realistic_magical_garden",
        iconEmoji = "🌙",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_3d_panda",
        label = "3D Panda",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF064E3B), Color(0xFF022C22), Color(0xFF042F2E)),
        styleString = "animated:realistic_3d_panda",
        iconEmoji = "🐼",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_rose_garden",
        label = "Rose Garden",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF2A0614), Color(0xFF4C0519), Color(0xFF881337)),
        styleString = "animated:realistic_rose_garden",
        iconEmoji = "🌹",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_fantasy_forest",
        label = "Sunlit Forest",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF052E16), Color(0xFF14532D), Color(0xFF166534)),
        styleString = "animated:realistic_fantasy_forest",
        iconEmoji = "🌲",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_glowing_mushrooms",
        label = "Glow Mushrooms",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF042F2E), Color(0xFF134E4A), Color(0xFF0E7490)),
        styleString = "animated:realistic_glowing_mushrooms",
        iconEmoji = "🍄",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_magical_waterfall",
        label = "Crystal Waterfall",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF082F49), Color(0xFF0369A1), Color(0xFF0284C7)),
        styleString = "animated:realistic_magical_waterfall",
        iconEmoji = "🌊",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_butterfly_haven",
        label = "Realistic Butterflies",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF2E1065), Color(0xFF581C87), Color(0xFF7E22CE)),
        styleString = "animated:realistic_butterfly_haven",
        iconEmoji = "🦋",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_firefly_glade",
        label = "Firefly Glade",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF1E293B)),
        styleString = "animated:realistic_firefly_glade",
        iconEmoji = "✨",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_floating_petals",
        label = "Cherry Grove",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF3B0724), Color(0xFF500724), Color(0xFF831843)),
        styleString = "animated:realistic_floating_petals",
        iconEmoji = "🌸",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_luxury_romantic",
        label = "Luxury Gold & Velvet",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF1C0A26), Color(0xFF3B0764), Color(0xFF4C0519)),
        styleString = "animated:realistic_luxury_romantic",
        iconEmoji = "💎",
        isAnimated = true
    ),
    TextPostBackgroundTemplate(
        id = "animated:realistic_3d_lion",
        label = "3D Golden Lion",
        category = AnimatedBgCategory.REALISTIC,
        baseColors = listOf(Color(0xFF451A03), Color(0xFF78350F), Color(0xFF92400E)),
        styleString = "animated:realistic_3d_lion",
        iconEmoji = "🦁",
        isAnimated = true
    )
)

/**
 * Helper to find template by ID or style string.
 */
fun findBackgroundTemplate(styleOrId: String?): TextPostBackgroundTemplate? {
    if (styleOrId.isNullOrBlank()) return null
    val stripped = styleOrId.trim().removeSurrounding("\"").replace("\\\"", "\"")
    val firstPart = stripped.substringBefore("|").trim()
    val idWithPrefix = if (firstPart.startsWith("animated:")) firstPart else "animated:$firstPart"
    val idWithoutPrefix = firstPart.removePrefix("animated:").trim()

    return ALL_TEXT_POST_BACKGROUNDS.find {
        it.id.equals(firstPart, ignoreCase = true) ||
        it.id.equals(idWithPrefix, ignoreCase = true) ||
        it.id.equals(idWithoutPrefix, ignoreCase = true) ||
        it.styleString.equals(firstPart, ignoreCase = true) ||
        it.styleString.equals(idWithPrefix, ignoreCase = true)
    } ?: ALL_TEXT_POST_BACKGROUNDS.find {
        it.isAnimated && firstPart.contains(it.id.removePrefix("animated:"), ignoreCase = true)
    }
}

/**
 * Text formatting options stored optionally inside backgroundStyle string.
 */
data class TextPostStyling(
    val alignment: TextAlign = TextAlign.Center,
    val textColor: Color = Color.White,
    val fontSizeSp: Int = 23,
    val fontStyleName: String = "bold"
) {
    companion object {
        fun parse(styleString: String?): TextPostStyling {
            if (styleString.isNullOrBlank()) return TextPostStyling()
            val clean = styleString.trim().removeSurrounding("\"").replace("\\\"", "\"")
            var align = TextAlign.Center
            var color = Color.White
            var size = 23
            var font = "bold"

            val parts = clean.split("|")
            for (p in parts) {
                val kv = p.trim().split(":")
                if (kv.size == 2) {
                    val k = kv[0].trim().lowercase()
                    val v = kv[1].trim()
                    when (k) {
                        "align" -> align = when (v.lowercase()) {
                            "left", "start" -> TextAlign.Start
                            "right", "end" -> TextAlign.End
                            else -> TextAlign.Center
                        }
                        "color" -> try {
                            if (v.startsWith("#")) {
                                val c = java.lang.Long.parseLong(v.removePrefix("#"), 16)
                                color = if (v.length == 7) Color(0xFF000000 or c) else Color(c)
                            }
                        } catch (_: Exception) {}
                        "size" -> size = v.toIntOrNull()?.coerceIn(16, 32) ?: 23
                        "font" -> font = v
                    }
                }
            }
            return TextPostStyling(align, color, size, font)
        }
    }
}
