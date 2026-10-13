package com.stagehub.entreprise.dto;

import com.stagehub.entreprise.entity.StatutValidation;

public record EntrepriseResponse(
        Long id,
        String email,
        String nom,
        String secteur,
        String ville,
        String description,
        StatutValidation statutValidation
) {
}
