package com.stagehub.ai.event;

import java.time.Instant;
import java.util.List;

/**
 * État de l'analyse d'une candidature, publié par l'outbox dans {@code stagehub.candidature.analyse}
 * (clé = id de la candidature) à chaque changement : EN_COURS au début d'une tentative, EN_ATTENTE quand une nouvelle
 * tentative est programmée après une panne, puis TERMINEE ou ECHEC. Une seule clé, un seul topic : Kafka livre ces
 * états dans l'ordre. Lu par candidature-service, qui les affiche à l'entreprise. Autonome : tout ce qu'il faut pour
 * l'affichage, rien de plus (ni le texte du CV, ni l'offre).
 *
 * @param statut               EN_COURS, EN_ATTENTE, TERMINEE ou ECHEC
 * @param motifEchec           cause d'un échec (CV_ABSENT, CV_ILLISIBLE, OFFRE_INTROUVABLE, ANALYSE_IMPOSSIBLE), sinon null
 * @param prochaineTentative   EN_ATTENTE après une panne : heure de la prochaine tentative, sinon null
 * @param score                score indicatif de 0 à 100, donné par le modèle (TERMINEE seulement)
 * @param justification        deux phrases au plus, du modèle
 * @param resume               cinq lignes au plus résumant le CV, du modèle
 * @param technologiesTrouvees technologies de l'offre retrouvées dans le CV (comptage déterministe, sans le modèle)
 * @param technologiesOffre    nombre de technologies de l'offre (« 3 sur 4 »)
 * @param alerteEcart          écart flagrant entre le score du modèle et la part des technologies retrouvées
 * @param contenuSuspect       phrases qui ressemblent à des consignes adressées au modèle (heuristique, contournable)
 * @param modele               modèle utilisé (ex. qwen3.5:4b)
 * @param date                 moment de ce changement d'état
 */
public record AnalyseCandidature(
        Long candidatureId,
        String statut,
        String motifEchec,
        Instant prochaineTentative,
        Integer score,
        String justification,
        List<String> resume,
        List<String> technologiesTrouvees,
        Integer technologiesOffre,
        boolean alerteEcart,
        boolean contenuSuspect,
        String modele,
        Instant date
) {

    /** État sans résultat (EN_COURS, EN_ATTENTE, ECHEC). */
    public static AnalyseCandidature etat(Long candidatureId, String statut, String motifEchec,
                                          Instant prochaineTentative, Instant date) {
        return new AnalyseCandidature(candidatureId, statut, motifEchec, prochaineTentative, null, null, null, null,
                null, false, false, null, date);
    }
}
