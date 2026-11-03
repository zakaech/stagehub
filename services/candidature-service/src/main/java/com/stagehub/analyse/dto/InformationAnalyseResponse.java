package com.stagehub.analyse.dto;

/**
 * Ce que l'étudiant doit savoir avant de postuler : qui analysera son CV. Indépendant de {@code IA_ACTIVE} :
 * une candidature déposée pendant que l'analyse est arrêtée sera analysée plus tard, par ce fournisseur.
 */
public record InformationAnalyseResponse(FournisseurIa fournisseur) {
}
