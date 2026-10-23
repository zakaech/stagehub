package com.stagehub.offre.entity;

import com.stagehub.common.util.Textes;
import com.stagehub.entreprise.entity.Entreprise;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "offre")
@Getter
@Setter
public class Offre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String ville;

    @Column(nullable = false)
    private String domaine;

    @ElementCollection
    @CollectionTable(name = "offre_technologie", joinColumns = @JoinColumn(name = "offre_id"))
    @Column(name = "technologie", nullable = false)
    private Set<String> technologies = new LinkedHashSet<>();

    @Column(name = "duree_mois", nullable = false)
    private Integer dureeMois;

    @Column(name = "date_limite", nullable = false)
    private LocalDate dateLimite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutOffre statut = StatutOffre.OUVERTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    /** Une offre accepte des candidatures si elle est ouverte et que sa date limite n'est pas dépassée. */
    public boolean accepteCandidatures(LocalDate aujourdhui) {
        return statut == StatutOffre.OUVERTE && !dateLimiteDepassee(aujourdhui);
    }

    /** La date limite elle-même est encore valable : elle n'est dépassée qu'à partir du lendemain. */
    public boolean dateLimiteDepassee(LocalDate aujourdhui) {
        return dateLimite.isBefore(aujourdhui);
    }

    public boolean appartientA(Long entrepriseId) {
        return entreprise.getId().equals(entrepriseId);
    }

    /**
     * Nettoie les champs servant aux filtres : espaces superflus retirés, technologies dédoublonnées sans tenir
     * compte de la casse ("Java" et "java " → une seule, la première saisie). Les filtres comparent en minuscules.
     */
    public void normaliserChampsDeFiltre() {
        ville = Textes.nettoyer(ville);
        domaine = Textes.nettoyer(domaine);

        Set<String> vues = new HashSet<>();
        Set<String> nettoyees = new LinkedHashSet<>();
        for (String technologie : technologies) {
            String propre = Textes.nettoyer(technologie);
            if (propre != null && !propre.isEmpty() && vues.add(propre.toLowerCase(Locale.ROOT))) {
                nettoyees.add(propre);
            }
        }
        // On modifie la collection existante (suivie par Hibernate) plutôt que de la remplacer
        technologies.clear();
        technologies.addAll(nettoyees);
    }
}
