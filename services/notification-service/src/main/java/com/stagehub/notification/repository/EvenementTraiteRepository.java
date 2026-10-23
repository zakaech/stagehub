package com.stagehub.notification.repository;

import com.stagehub.notification.entity.EvenementTraite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface EvenementTraiteRepository extends JpaRepository<EvenementTraite, UUID> {

    /**
     * Inscrit l'événement s'il ne l'est pas déjà. Renvoie 1 s'il vient d'être inscrit (à traiter), 0 s'il l'était
     * (doublon). ON CONFLICT DO NOTHING : la clé primaire tranche, même si deux traitements arrivaient en même temps.
     */
    @Modifying
    @Query(value = """
            INSERT INTO evenement_traite (id, type, date_traitement) VALUES (:id, :type, :date)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int inscrire(UUID id, String type, Instant date);
}
