package com.stagehub.auth.repository;

import com.stagehub.auth.entity.StatutCompte;
import com.stagehub.auth.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Inscriptions restées en attente (saga interrompue) avant une date donnée. */
    List<Utilisateur> findByStatutAndDateCreationBefore(StatutCompte statut, Instant limite);
}
