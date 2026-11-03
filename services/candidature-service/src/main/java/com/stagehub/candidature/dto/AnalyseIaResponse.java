package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.AnalyseIa;
import com.stagehub.candidature.entity.StatutAnalyse;

import java.time.Instant;
import java.util.List;

/**
 * Analyse de la candidature par l'IA, telle que l'entreprise la voit. Jamais envoyée à l'étudiant.
 *
 * @param statut EN_ATTENTE, EN_COURS, TERMINEE, ECHEC, ou DESACTIVEE : en attente alors que l'IA est arrêtée
 *               (calculé à la lecture : rien à migrer quand on active ou arrête l'IA)
 * @param score  score indicatif de 0 à 100, seulement si TERMINEE
 */
public record AnalyseIaResponse(
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

    static final String DESACTIVEE = "DESACTIVEE";

    /** Null pour une candidature antérieure à l'analyse automatique, jamais envoyée à l'analyse. */
    public static AnalyseIaResponse de(AnalyseIa analyse, boolean iaActive) {
        if (analyse == null || analyse.getStatut() == null) {
            return null;
        }
        String statut = analyse.getStatut() == StatutAnalyse.EN_ATTENTE && !iaActive
                ? DESACTIVEE : analyse.getStatut().name();
        if (analyse.getStatut() != StatutAnalyse.TERMINEE) {
            return new AnalyseIaResponse(statut, analyse.getMotifEchec(), analyse.getProchaineTentative(), null, null,
                    List.of(), List.of(), null, false, false, null, analyse.getDate());
        }
        return new AnalyseIaResponse(statut, null, null, analyse.getScore(), analyse.getJustification(),
                List.copyOf(analyse.getResume()), List.copyOf(analyse.getTechnologiesTrouvees()),
                analyse.getTechnologiesOffre(), analyse.isAlerteEcart(), analyse.isContenuSuspect(),
                analyse.getModele(), analyse.getDate());
    }
}
