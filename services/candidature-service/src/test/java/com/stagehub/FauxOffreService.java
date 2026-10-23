package com.stagehub;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stagehub.candidature.client.OffreInterne;
import com.stagehub.candidature.dto.OffreResumeResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Faux offre-service pour les tests d'intégration de candidature-service : un serveur WireMock démarré une fois
 * pour toute l'exécution, vers lequel pointe le client Feign (voir {@link IntegrationTestBase}). Chaque test y
 * déclare les offres dont il a besoin, avec des ids uniques : les tests restent indépendants.
 */
public final class FauxOffreService {

    public static final WireMockServer SERVEUR = new WireMockServer(options().dynamicPort());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static {
        SERVEUR.start();
    }

    private FauxOffreService() {
    }

    public static String url() {
        return SERVEUR.baseUrl();
    }

    /** Offre ouverte (qui accepte les candidatures) de l'entreprise donnée. */
    public static OffreInterne offreOuverte(Long offreId, Long entrepriseId) {
        OffreInterne offre = new OffreInterne(offreId, "Stage PFE Java", "Rabat", "OUVERTE",
                LocalDate.now().plusDays(30), entrepriseId, "Acme", true);
        declarer(offre);
        return offre;
    }

    /**
     * Réponses d'offre-service pour cette offre : son détail ({@code GET /internal/offres/{id}}) et son résumé
     * ({@code GET /internal/offres?ids=...}, quand la page ne contient qu'elle). Redéclarer la même offre remplace
     * les réponses précédentes (ex. offre fermée entre-temps).
     */
    public static void declarer(OffreInterne offre) {
        SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres/" + offre.id()))
                .willReturn(okJson(JSON.writeValueAsString(offre))));
        OffreResumeResponse resume = new OffreResumeResponse(offre.id(), offre.titre(), offre.entrepriseNom(),
                offre.ville(), offre.statut());
        SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres"))
                .withQueryParam("ids", equalTo(offre.id().toString()))
                .willReturn(okJson(JSON.writeValueAsString(List.of(resume)))));
    }
}
