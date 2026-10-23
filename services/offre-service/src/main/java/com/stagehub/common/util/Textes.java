package com.stagehub.common.util;

/** Petites opérations sur les textes saisis par les utilisateurs. */
public final class Textes {

    private Textes() {
    }

    /** Retire les espaces autour et remplace les suites d'espaces internes par un seul : "  Casa   blanca " → "Casa blanca". */
    public static String nettoyer(String texte) {
        return texte == null ? null : texte.strip().replaceAll("\\s+", " ");
    }
}
