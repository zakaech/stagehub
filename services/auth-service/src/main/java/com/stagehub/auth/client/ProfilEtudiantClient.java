package com.stagehub.auth.client;

import com.stagehub.auth.dto.ProfilEtudiant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client HTTP déclaratif (OpenFeign) vers le service qui détient les profils étudiants : Spring génère
 * l'implémentation, résout le nom du service dans Eureka et répartit les appels entre ses instances.
 * Nom configurable (candidature-service) : il a changé au fil de l'extraction du monolithe sans toucher ce code.
 */
@FeignClient(name = "${stagehub.services.profils-etudiant}", contextId = "profilsEtudiant", path = "/internal/etudiants")
public interface ProfilEtudiantClient {

    @PostMapping
    void creer(@RequestBody CreationProfilEtudiant demande);

    @GetMapping("/{id}")
    ProfilEtudiant lire(@PathVariable("id") Long id);

    /** Compensation de la saga d'inscription ; idempotent côté service (profil absent → 204). */
    @DeleteMapping("/{id}")
    void supprimer(@PathVariable("id") Long id);
}
