package com.stagehub.offre.dto;

import com.stagehub.offre.entity.StatutOffre;

import java.time.LocalDate;
import java.util.Set;

public record OffreResponse(
        Long id,
        String titre,
        String description,
        String ville,
        String domaine,
        Set<String> technologies,
        Integer dureeMois,
        LocalDate dateLimite,
        StatutOffre statut,
        Long entrepriseId,
        String entrepriseNom
) {
}
