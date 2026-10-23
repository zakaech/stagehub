package com.stagehub.outbox.repository;

import com.stagehub.outbox.entity.OutboxEvenement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvenement, UUID> {

    /**
     * Les plus anciens événements pas encore publiés, verrouillés jusqu'à la fin de la transaction.
     * FOR UPDATE SKIP LOCKED : une autre instance du relais saute les lignes déjà prises au lieu de les attendre ;
     * plusieurs instances peuvent donc publier en parallèle sans jamais prendre le même événement.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE date_publication IS NULL
            ORDER BY date_creation
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvenement> verrouillerAPublier(int limite);

    long countByDatePublicationIsNull();

    @Modifying
    @Query("delete from OutboxEvenement e where e.datePublication < :avant")
    int supprimerPubliesAvant(Instant avant);
}
