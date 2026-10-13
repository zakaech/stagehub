package com.stagehub.candidature.entity;

import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.offre.entity.Offre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "candidature",
        uniqueConstraints = @UniqueConstraint(name = "uk_candidature_etudiant_offre", columnNames = {"etudiant_id", "offre_id"}))
@Getter
@Setter
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offre_id", nullable = false)
    private Offre offre;

    @Column(name = "lettre_motivation")
    private String lettreMotivation;

    /** Pas de setter : le statut ne change que via {@link #changerStatut}, qui applique la machine à états. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCandidature statut = StatutCandidature.EN_ATTENTE;

    @Column(name = "date_candidature", nullable = false, updatable = false)
    private Instant dateCandidature;

    /** Rempli en phase 8 (Spring AI). */
    @Column(name = "score_matching")
    private Integer scoreMatching;

    public void changerStatut(StatutCandidature nouveauStatut) {
        if (!statut.peutPasserA(nouveauStatut)) {
            throw new TransitionStatutInvalideException(
                    "Transition de statut impossible : %s → %s".formatted(statut, nouveauStatut));
        }
        statut = nouveauStatut;
    }

    @PrePersist
    void avantPersistance() {
        if (dateCandidature == null) {
            dateCandidature = Instant.now();
        }
    }
}
