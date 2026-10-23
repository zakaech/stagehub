package com.stagehub.admin.dto;

import com.stagehub.offre.dto.TechnologieStat;

import java.util.List;

/**
 * Partie « offres et entreprises » des statistiques de l'admin (phase 7 c). La page Angular la combine avec la
 * partie « candidatures », fournie par le service qui les détient.
 */
public record StatistiquesOffresResponse(
        long nombreOffres,
        long nombreOffresOuvertes,
        List<TechnologieStat> topTechnologies,
        long entreprisesEnAttente
) {
}
