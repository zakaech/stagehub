package com.stagehub.ai.repository;

import com.stagehub.ai.entity.DemandeAnalyse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface DemandeAnalyseRepository extends JpaRepository<DemandeAnalyse, Long> {

    /**
     * Prochaine demande à analyser, verrouillée jusqu'à la fin de la transaction : en attente et dont l'heure est venue,
     * ou restée EN_COURS au-delà du bail (service arrêté pendant l'analyse). {@code SKIP LOCKED} : une autre instance
     * d'ai-service passe à la demande suivante au lieu d'attendre (même principe que le relais de l'outbox).
     */
    @Query(value = """
            select * from demande_analyse
            where (statut = 'EN_ATTENTE' and (prochaine_tentative is null or prochaine_tentative <= :maintenant))
               or (statut = 'EN_COURS' and debut_analyse < :finDuBail)
            order by date_reception
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<DemandeAnalyse> verrouillerProchaine(Instant maintenant, Instant finDuBail);
}
