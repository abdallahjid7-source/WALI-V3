package com.example.wali.chat

/**
 * Filtre de modération de contenu textuel pour le chat multijoueur.
 *
 * Contient une liste modifiable de termes inappropriés (français, anglais et termes locaux).
 * Les utilisateurs peuvent facilement enrichir cette liste dans ce fichier dédié.
 */
object WaliProfanityFilter {

    /**
     * Liste des termes interdits en minuscules.
     * Facilement extensible avec des expressions en bambara ou en français local.
     */
    private val FORBIDDEN_WORDS = setOf(
        // Français
        "connard", "connasse", "salope", "putain", "merde", "encule", "enculer",
        "batard", "bâtard", "fdp", "ntm", "nique", "niquer", "idiot", "cretin",
        "crétin", "abruti", "imbecile", "imbécile", "ferme ta gueule", "ta gueule",
        "degage", "dégage", "salaud",

        // Anglais
        "fuck", "bitch", "asshole", "shit", "bastard", "idiot", "moron", "cunt",
        "dick", "pussy", "retard", "fucker", "motherfucker",

        // Termes d'insulte courants en Bambara / Mandingue (à enrichir par le mainteneur)
        "denkuru", "faden", "n'koni", "tchié"
    )

    /**
     * Vérifie si le texte contient un terme interdit (insensible à la casse et aux ponctuations simples).
     */
    fun containsProfanity(text: String): Boolean {
        val normalized = normalize(text)
        val words = normalized.split("\\s+".toRegex())
        for (word in words) {
            if (FORBIDDEN_WORDS.contains(word)) {
                return true
            }
        }
        // Vérifie également les expressions composées
        for (forbidden in FORBIDDEN_WORDS) {
            if (forbidden.contains(" ") && normalized.contains(forbidden)) {
                return true
            }
        }
        return false
    }

    /**
     * Masque les termes grossiers par des étoiles ("***") sans altérer le reste du message.
     */
    fun filterOrMask(text: String): String {
        var result = text
        for (forbidden in FORBIDDEN_WORDS) {
            val regex = Regex("(?i)\\b" + Regex.escape(forbidden) + "\\b")
            result = result.replace(regex) { matchResult ->
                "*".repeat(matchResult.value.length.coerceAtLeast(3))
            }
        }
        return result
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace('é', 'e')
            .replace('è', 'e')
            .replace('ê', 'e')
            .replace('ë', 'e')
            .replace('à', 'a')
            .replace('â', 'a')
            .replace('ô', 'o')
            .replace('î', 'i')
            .replace('ï', 'i')
            .replace('ù', 'u')
            .replace('û', 'u')
            .replace('ç', 'c')
            .replace("[^a-z0-9\\s]".toRegex(), " ")
    }
}
