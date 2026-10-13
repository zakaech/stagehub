package com.stagehub.offre.repository;

import com.stagehub.offre.dto.TechnologieStat;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OffreRepository extends JpaRepository<Offre, Long> {

    // L'entreprise est chargée par jointure (ManyToOne : compatible avec la pagination) ;
    // les technologies sont chargées par lots grâce à hibernate.default_batch_fetch_size.
    @EntityGraph(attributePaths = "entreprise")
    Page<Offre> findByStatut(StatutOffre statut, Pageable pageable);

    @EntityGraph(attributePaths = "entreprise")
    Page<Offre> findByEntrepriseId(Long entrepriseId, Pageable pageable);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Offre> findByIdAndStatut(Long id, StatutOffre statut);

    long countByStatut(StatutOffre statut);

    @Query("""
            select new com.stagehub.offre.dto.TechnologieStat(t, count(o))
            from Offre o join o.technologies t
            group by t
            order by count(o) desc, t asc
            """)
    List<TechnologieStat> compterParTechnologie(Pageable pageable);
}
