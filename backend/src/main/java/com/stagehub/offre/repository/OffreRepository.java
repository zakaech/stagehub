package com.stagehub.offre.repository;

import com.stagehub.offre.dto.TechnologieStat;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JpaSpecificationExecutor ajoute findAll(Specification, Pageable), utilisé par la recherche filtrée. */
public interface OffreRepository extends JpaRepository<Offre, Long>, JpaSpecificationExecutor<Offre> {

    // L'entreprise est chargée par jointure (ManyToOne : compatible avec la pagination) ;
    // les technologies sont chargées par lots grâce à hibernate.default_batch_fetch_size.
    @Override
    @EntityGraph(attributePaths = "entreprise")
    Page<Offre> findAll(Specification<Offre> specification, Pageable pageable);

    @EntityGraph(attributePaths = "entreprise")
    Page<Offre> findByEntrepriseId(Long entrepriseId, Pageable pageable);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Offre> findByIdAndStatut(Long id, StatutOffre statut);

    long countByStatut(StatutOffre statut);

    /**
     * Mise à jour en masse : une seule requête SQL, sans charger les entités. clearAutomatically vide ensuite le
     * contexte de persistance, qui contiendrait sinon des offres encore marquées OUVERTE en mémoire.
     * Strictement avant aujourd'hui : le jour de la date limite, on peut encore postuler.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Offre o set o.statut = :fermee
            where o.statut = :ouverte and o.dateLimite < :aujourdhui
            """)
    int fermerOffresExpirees(LocalDate aujourdhui, StatutOffre ouverte, StatutOffre fermee);

    @Query("""
            select new com.stagehub.offre.dto.TechnologieStat(t, count(o))
            from Offre o join o.technologies t
            group by t
            order by count(o) desc, t asc
            """)
    List<TechnologieStat> compterParTechnologie(Pageable pageable);

    // Valeurs des listes déroulantes : un représentant par groupe "lower(valeur)", donc
    // "Rabat" et "rabat" ne donnent qu'une option (min() choisit l'orthographe de façon déterministe).

    @Query("""
            select min(o.ville) from Offre o
            where o.statut = :statut
            group by lower(o.ville)
            order by lower(o.ville)
            """)
    List<String> villesDistinctes(StatutOffre statut);

    @Query("""
            select min(o.domaine) from Offre o
            where o.statut = :statut
            group by lower(o.domaine)
            order by lower(o.domaine)
            """)
    List<String> domainesDistincts(StatutOffre statut);

    @Query("""
            select min(t) from Offre o join o.technologies t
            where o.statut = :statut
            group by lower(t)
            order by lower(t)
            """)
    List<String> technologiesDistinctes(StatutOffre statut);
}
