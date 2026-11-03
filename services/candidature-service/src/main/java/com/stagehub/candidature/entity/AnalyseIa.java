package com.stagehub.candidature.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyse de la candidature par l'IA, colonnes de la table candidature : dernier état reçu d'ai-service
 * et, une fois TERMINEE, son résultat. Le score est indicatif ; rien ici ne change le statut de la candidature.
 */
@Embeddable
@Getter
@Setter
public class AnalyseIa {

    /** Null : candidature antérieure à l'analyse automatique, jamais envoyée à l'analyse. */
    @Enumerated(EnumType.STRING)
    @Column(name = "analyse_statut")
    private StatutAnalyse statut;

    @Column(name = "analyse_motif_echec")
    private String motifEchec;

    @Column(name = "analyse_prochaine_tentative")
    private Instant prochaineTentative;

    /** Score indicatif de 0 à 100. */
    @Column(name = "score_matching")
    private Integer score;

    @Column(name = "analyse_justification")
    private String justification;

    @Column(name = "analyse_resume")
    @Convert(converter = ListeLignesConverter.class)
    private List<String> resume = new ArrayList<>();

    @Column(name = "technologies_trouvees")
    @Convert(converter = ListeLignesConverter.class)
    private List<String> technologiesTrouvees = new ArrayList<>();

    @Column(name = "technologies_offre")
    private Integer technologiesOffre;

    @Column(name = "alerte_ecart", nullable = false)
    private boolean alerteEcart;

    @Column(name = "contenu_suspect", nullable = false)
    private boolean contenuSuspect;

    @Column(name = "analyse_modele")
    private String modele;

    /** Moment du dernier changement d'état. */
    @Column(name = "date_analyse")
    private Instant date;
}
