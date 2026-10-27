package com.stagehub.ai.entity;

/** Pourquoi une analyse n'a pas abouti (transmis à candidature-service, qui l'affiche en clair). */
public enum MotifEchec {
    /** La candidature n'a pas de CV : rien à analyser (définitif). */
    CV_ABSENT,
    /** PDF abîmé, protégé ou scanné (sans texte) : définitif. */
    CV_ILLISIBLE,
    /** L'offre n'existe plus dans offre-service : définitif. */
    OFFRE_INTROUVABLE,
    /** Pannes successives (modèle, réseau) ou réponses invalides du modèle, jusqu'à la dernière tentative. */
    ANALYSE_IMPOSSIBLE
}
