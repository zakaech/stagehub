package com.stagehub.candidature.entity;

import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.etudiant.entity.Etudiant;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    /** Id de l'offre, dans offre-service (plus de clé étrangère : autre base, depuis la phase 7 c). */
    @Column(name = "offre_id", nullable = false, updatable = false)
    private Long offreId;

    // Instantané de l'offre au moment de postuler (dénormalisation volontaire, comme le CV figé) :
    //  - entrepriseId ne change jamais : la règle de propriété (une entreprise ne voit que ses candidatures) se vérifie
    //    localement, sans appel réseau ;
    //  - titre, entreprise, ville : l'email de changement de statut et le repli de la liste « mes candidatures »
    //    quand offre-service ne répond pas (sinon, la liste affiche les données à jour d'offre-service).
    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    @Column(name = "offre_titre", nullable = false, updatable = false)
    private String offreTitre;

    @Column(name = "entreprise_nom", nullable = false, updatable = false)
    private String entrepriseNom;

    @Column(name = "offre_ville", updatable = false)
    private String offreVille;

    @Column(name = "lettre_motivation")
    private String lettreMotivation;

    /** Pas de setter : le statut ne change que via {@link #changerStatut}, qui applique la machine à états. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCandidature statut = StatutCandidature.EN_ATTENTE;

    @Column(name = "date_candidature", nullable = false, updatable = false)
    private Instant dateCandidature;

    /** Nom du fichier CV en vigueur quand l'étudiant a postulé (null s'il n'en avait pas). */
    @Column(name = "cv_fichier", updatable = false)
    private String cvFichier;

    /** Analyse par l'IA (phase 8) : état reçu d'ai-service, puis score indicatif, justification, résumé du CV. */
    @Embedded
    private AnalyseIa analyse = new AnalyseIa();

    public boolean concerneLEntreprise(Long idEntreprise) {
        return entrepriseId.equals(idEntreprise);
    }

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
