package com.stagehub.offre.repository;

import com.stagehub.common.util.Textes;
import com.stagehub.offre.dto.OffreFiltre;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Specifications JPA : chaque méthode produit une condition WHERE réutilisable ; on assemble celles
 * qui correspondent aux filtres reçus au lieu d'écrire une méthode de repository par combinaison.
 */
public final class OffreSpecifications {

    private OffreSpecifications() {
    }

    /** Offres OUVERTE correspondant à tous les critères renseignés (ET entre critères, OU entre technologies). */
    public static Specification<Offre> ouvertesCorrespondantA(OffreFiltre filtre) {
        List<Specification<Offre>> conditions = new ArrayList<>();
        conditions.add(statut(StatutOffre.OUVERTE));
        normaliser(filtre.ville()).ifPresent(ville -> conditions.add(egalSansCasse("ville", ville)));
        normaliser(filtre.domaine()).ifPresent(domaine -> conditions.add(egalSansCasse("domaine", domaine)));

        List<String> technologies = filtre.technologies().stream()
                .map(OffreSpecifications::normaliser)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (!technologies.isEmpty()) {
            conditions.add(auMoinsUneTechnologie(technologies));
        }
        return Specification.allOf(conditions);
    }

    static Specification<Offre> statut(StatutOffre statut) {
        return (root, query, cb) -> cb.equal(root.get("statut"), statut);
    }

    /** lower(champ) = valeur (déjà en minuscules) : peut utiliser l'index lower(...) de la migration V2. */
    static Specification<Offre> egalSansCasse(String champ, String valeurMinuscule) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get(champ)), valeurMinuscule);
    }

    /**
     * EXISTS (select 1 from offre_technologie t where t.offre_id = offre.id and lower(t.technologie) in (...)).
     * Une sous-requête plutôt qu'une jointure : une offre correspondant à deux technologies n'apparaît
     * qu'une fois, sans DISTINCT, et le total de la pagination reste juste.
     */
    static Specification<Offre> auMoinsUneTechnologie(List<String> technologiesMinuscules) {
        return (root, query, cb) -> {
            Subquery<Long> sousRequete = Objects.requireNonNull(query).subquery(Long.class);
            Root<Offre> offre = sousRequete.from(Offre.class);
            Join<Offre, String> technologie = offre.join("technologies");
            sousRequete.select(offre.get("id"))
                    .where(cb.equal(offre, root), cb.lower(technologie).in(technologiesMinuscules));
            return cb.exists(sousRequete);
        };
    }

    /** Même nettoyage qu'à l'enregistrement, puis minuscules ; vide → absent. */
    private static Optional<String> normaliser(String valeur) {
        String propre = Textes.nettoyer(valeur);
        return propre == null || propre.isEmpty()
                ? Optional.empty()
                : Optional.of(propre.toLowerCase(Locale.ROOT));
    }
}
