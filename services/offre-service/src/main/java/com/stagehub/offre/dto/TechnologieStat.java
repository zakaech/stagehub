package com.stagehub.offre.dto;

/** Projection JPQL : nombre d'offres demandant une technologie. */
public record TechnologieStat(String technologie, Long nombreOffres) {
}
