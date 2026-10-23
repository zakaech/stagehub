package com.stagehub.etudiant.repository;

import com.stagehub.etudiant.entity.Etudiant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {

    /**
     * SELECT ... FOR UPDATE : verrouille la ligne de l'étudiant jusqu'à la fin de la transaction.
     * Le dépôt d'un CV et une candidature du même étudiant s'exécutent ainsi l'un après l'autre,
     * ce qui garantit que la candidature fige bien le CV en vigueur.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Etudiant e where e.id = :id")
    Optional<Etudiant> findAvecVerrou(Long id);
}
