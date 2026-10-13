package com.stagehub.entreprise.repository;

import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

    @Override
    @EntityGraph(attributePaths = "utilisateur")
    Page<Entreprise> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "utilisateur")
    Page<Entreprise> findByStatutValidation(StatutValidation statutValidation, Pageable pageable);

    long countByStatutValidation(StatutValidation statutValidation);
}
