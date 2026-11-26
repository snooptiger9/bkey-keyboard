package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * French text for the keyboard's own drawers (clipboard, emoji, GIF), keyed by the English
 * string the drawer code already carries. Anything missing falls back to the English text.
 */
final class FrenchUi {
    private FrenchUi() {}

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        TEXT.put("Screenshot copied — paste it here", "Capture d’écran copiée — collez-la ici");
        TEXT.put("Delete", "Supprimer");
        TEXT.put("Screenshot", "Capture d’écran");
        TEXT.put("Incognito mode is on\nClipboard is hidden and nothing new is being saved",
                "Le mode navigation privée est activé\nLe presse-papiers est masqué et rien de nouveau n’est enregistré");
        TEXT.put("Nothing copied yet", "Rien n’a encore été copié");
        TEXT.put("Unpin", "Détacher");
        TEXT.put("Pin", "Épingler");
        TEXT.put("Powered by KLIPY", "Propulsé par KLIPY");
        TEXT.put("Search KLIPY...", "Rechercher sur KLIPY...");
        TEXT.put("Undo", "Annuler");
        TEXT.put("No recent emojis yet", "Aucun emoji récent pour l’instant");
        TEXT.put("  •  Long-press any emoji to delete it", "  •  Appuyez longuement sur un emoji pour le supprimer");
        TEXT.put("  •  Long-press any GIF to delete it", "  •  Appuyez longuement sur un GIF pour le supprimer");
        TEXT.put("Loading...", "Chargement...");
        TEXT.put("Couldn't load: ", "Chargement impossible : ");
        TEXT.put("No results", "Aucun résultat");
        TEXT.put("Recently used", "Récemment utilisés");
        TEXT.put("Search results", "Résultats de recherche");
        TEXT.put("Trending now", "Tendances du moment");
        TEXT.put("This app doesn't allow sending GIFs here", "Cette application ne permet pas d’envoyer des GIF ici");
        TEXT.put("This app doesn't allow sending screenshots here", "Cette application ne permet pas d’envoyer des captures d’écran ici");
        TEXT.put("Couldn't load GIF", "Impossible de charger le GIF");
        TEXT.put("Microphone permission is needed", "L'autorisation du micro est nécessaire");
        TEXT.put("No internet connection for voice typing", "Pas de connexion Internet pour la saisie vocale");
        TEXT.put("Voice typing failed", "La saisie vocale a échoué");
        TEXT.put("Voice typing isn't available on this device", "La saisie vocale n'est pas disponible sur cet appareil");
        TEXT.put("GIF copied — paste it here", "GIF copié — collez-le ici");
    }

    /** French for the given English drawer text, or the English text itself if not translated yet. */
    static String get(String english) {
        String fr = TEXT.get(english);
        return fr != null ? fr : english;
    }

    static final String[] EMOJI_CATEGORIES = {
        "Récemment utilisés",
        "Visages et émotions",
        "Personnes et corps",
        "Nature, événements et islam",
        "Animaux",
        "Nourriture et boissons",
        "Voyages et lieux",
        "Symboles et drapeaux"
    };
}
