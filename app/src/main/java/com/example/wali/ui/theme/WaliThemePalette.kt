package com.example.wali.ui.theme

/**
 * Les trois thèmes visuels officiels de l'application Wali Songhaï.
 */
enum class WaliThemePalette(
    val title: String,
    val subtitle: String
) {
    SAHEL_JOUR(
        title = "Sahel Jour",
        subtitle = "Tons authentiques ocre, sable et terre cuite de Tombouctou"
    ),
    DESERT_NIGHT(
        title = "Nuit du Désert",
        subtitle = "Fond bleu nuit profond et or lunaire pour jeu nocturne"
    ),
    MODERN_LIGHT(
        title = "Moderne Clair",
        subtitle = "Minimaliste épuré blanc cassé, ardoise et touches terracotta"
    );

    companion object {
        val DEFAULT = SAHEL_JOUR

        fun fromName(name: String?): WaliThemePalette {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
