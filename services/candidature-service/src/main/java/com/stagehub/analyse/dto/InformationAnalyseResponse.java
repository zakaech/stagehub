package com.stagehub.analyse.dto;

/**
 * Ce que l'étudiant doit savoir avant de postuler : qui analysera son CV (spec §1). Indépendant de {@code IA_ACTIVE} :
 * une candidature déposée pendant que l'analyse est arrêtée sera analysée plus tard, par ce fournisseur.
 */
public record InformationAnalyseResponse(FournisseurIa fournisseur) {
}
