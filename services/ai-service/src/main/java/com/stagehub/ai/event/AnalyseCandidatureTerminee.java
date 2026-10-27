package com.stagehub.ai.event;

import java.time.Instant;
import java.util.List;

/**
 * Résultat de l'analyse d'une candidature (phase 8 d), publié par l'outbox dans {@code stagehub.candidature.analyse-terminee}
 * (clé = id de la candidature) et lu par candidature-service, qui l'affiche à l'entreprise. Autonome : tout ce qu'il
 * faut pour l'affichage, rien de plus (ni le texte du CV, ni l'offre).
 *
 * @param statut               TERMINEE ou ECHEC
 * @param motifEchec           cause d'un échec (CV_ABSENT, CV_ILLISIBLE, OFFRE_INTROUVABLE, ANALYSE_IMPOSSIBLE), sinon null
 * @param score                score indicatif de 0 à 100, donné par le modèle (null en cas d'échec)
 * @param justification        deux phrases au plus, du modèle
 * @param resume               cinq lignes au plus résumant le CV, du modèle
 * @param technologiesTrouvees technologies de l'offre retrouvées dans le CV (comptage déterministe, sans le modèle)
 * @param technologiesOffre    nombre de technologies de l'offre (« 3 sur 4 »)
 * @param alerteEcart          écart flagrant entre le score du modèle et la part des technologies retrouvées
 * @param contenuSuspect       phrases qui ressemblent à des consignes adressées au modèle (heuristique, contournable)
 * @param modele               modèle utilisé (ex. qwen3.5:4b)
 */
public record AnalyseCandidatureTerminee(
        Long candidatureId,
        String statut,
        String motifEchec,
        Integer score,
        String justification,
        List<String> resume,
        List<String> technologiesTrouvees,
        Integer technologiesOffre,
        boolean alerteEcart,
        boolean contenuSuspect,
        String modele,
        Instant dateAnalyse
) {
}
