package com.stagehub.auth.client;

import com.stagehub.auth.dto.ProfilEntreprise;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Client vers le service qui détient les profils entreprises (offre-service, nom configurable). */
@FeignClient(name = "${stagehub.services.profils-entreprise}", contextId = "profilsEntreprise", path = "/internal/entreprises")
public interface ProfilEntrepriseClient {

    @PostMapping
    void creer(@RequestBody CreationProfilEntreprise demande);

    @GetMapping("/{id}")
    ProfilEntreprise lire(@PathVariable("id") Long id);

    @DeleteMapping("/{id}")
    void supprimer(@PathVariable("id") Long id);
}
