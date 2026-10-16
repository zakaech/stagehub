package com.stagehub.offre.dto;

import java.util.List;

/** Valeurs proposées dans les listes déroulantes de filtres (offres ouvertes uniquement, sans doublons de casse). */
public record FiltresDisponiblesResponse(List<String> villes, List<String> domaines, List<String> technologies) {
}
