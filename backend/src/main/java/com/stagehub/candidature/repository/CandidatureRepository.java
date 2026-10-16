package com.stagehub.candidature.repository;

import com.stagehub.candidature.dto.StatutStat;
import com.stagehub.candidature.entity.Candidature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

    boolean existsByEtudiantIdAndOffreId(Long etudiantId, Long offreId);

    /** Un fichier CV encore référencé par une candidature ne doit pas être supprimé. */
    boolean existsByCvFichier(String cvFichier);

    @EntityGraph(attributePaths = {"offre", "offre.entreprise"})
    Page<Candidature> findByEtudiantId(Long etudiantId, Pageable pageable);

    @EntityGraph(attributePaths = {"offre", "etudiant", "etudiant.utilisateur"})
    Page<Candidature> findByOffreId(Long offreId, Pageable pageable);

    @Query("""
            select new com.stagehub.candidature.dto.StatutStat(c.statut, count(c))
            from Candidature c
            group by c.statut
            """)
    List<StatutStat> compterParStatut();
}
