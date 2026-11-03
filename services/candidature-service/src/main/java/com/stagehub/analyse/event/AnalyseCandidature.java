package com.stagehub.analyse.event;

import java.time.Instant;
import java.util.List;

/**
 * État de l'analyse d'une candidature, publié par ai-service dans {@code stagehub.candidature.analyse}.
 * Copie du contrat, propre à ce service (pas de bibliothèque d'événements partagée) ; les champs inconnus du message
 * sont ignorés, ce qui permet à ai-service d'en ajouter sans casser ce lecteur. Booléens en {@code Boolean} : un champ
 * absent vaut faux (Jackson 3 refuse par défaut une valeur absente pour un {@code boolean} : le message partait en
 * « .DLT », constaté par AnalyseResultatIT).
 *
 * @param statut EN_COURS, EN_ATTENTE (nouvelle tentative programmée), TERMINEE ou ECHEC
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
        Boolean alerteEcart,
        Boolean contenuSuspect,
        String modele,
        Instant date
) {
}
