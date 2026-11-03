package com.stagehub.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Candidature à analyser (score de matching et résumé du CV) : une par candidature. */
@Entity
@Table(name = "demande_analyse")
@Getter
@Setter
public class DemandeAnalyse {

    /** Id de la candidature dans candidature-service (autre base : pas de clé étrangère). */
    @Id
    @Column(name = "candidature_id")
    private Long candidatureId;

    @Column(name = "offre_id", nullable = false)
    private Long offreId;

    @Column(name = "etudiant_id", nullable = false)
    private Long etudiantId;

    @Column(name = "cv_fichier")
    private String cvFichier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAnalyse statut = StatutAnalyse.EN_ATTENTE;

    @Column(name = "date_reception", nullable = false)
    private Instant dateReception;

    /** Contexte de trace W3C reçu avec l'événement : l'analyse continuera la trace de la candidature (null : aucun). */
    private String traceparent;

    /** Tentatives déjà commencées. */
    @Column(nullable = false)
    private int tentatives;

    /** Pas de nouvelle tentative avant cette date (null : dès que possible). */
    @Column(name = "prochaine_tentative")
    private Instant prochaineTentative;

    @Column(name = "debut_analyse")
    private Instant debutAnalyse;

    @Column(name = "date_fin")
    private Instant dateFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif_echec")
    private MotifEchec motifEchec;

    /** Modèle qui a produit le résultat (ex. qwen3.5:4b). */
    private String modele;

    @Column(name = "duree_ms")
    private Long dureeMs;
}
