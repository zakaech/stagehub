package com.stagehub.candidature;

import com.stagehub.ApiDeTest;
import com.stagehub.FauxOffreService;
import com.stagehub.IntegrationTestBase;
import com.stagehub.candidature.repository.CandidatureRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportement de candidature-service quand offre-service va mal (OpenFeign + circuit breaker Resilience4j) : on ne prend
 * jamais une candidature non vérifiée (503), mais les listes restent consultables (mode dégradé).
 */
class OffreServiceIndisponibleIT extends IntegrationTestBase {

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    @Autowired
    private CandidatureRepository candidatureRepository;

    private ApiDeTest api;

    @BeforeEach
    void setUp() {
        api = new ApiDeTest(mockMvc);
        // Les appels réussis des autres tests (contexte partagé) compteraient dans la fenêtre du circuit
        refermerLesCircuits();
    }

    /** Le contexte Spring est partagé : on referme les circuits pour ne pas perturber les autres tests. */
    @AfterEach
    void refermerLesCircuits() {
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void postuler_offreServiceEnErreur_503SansRienEnregistrer() throws Exception {
        long offreId = ApiDeTest.nouvelId();
        FauxOffreService.SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres/" + offreId)).willReturn(serverError()));
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();

        postuler(offreId, etudiant)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ServiceIndisponible"));
        assertThat(candidatureRepository.existsByEtudiantIdAndOffreId(etudiant.id(), offreId)).isFalse();
    }

    @Test
    void postuler_offreServiceTropLent_503ApresLeDelaiDeLecture() throws Exception {
        long offreId = ApiDeTest.nouvelId();
        // 3,5 s > read-timeout (3 s) : Feign abandonne, réessaie une fois (erreur réseau sur un GET), puis renonce
        FauxOffreService.SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres/" + offreId))
                .willReturn(aResponse().withFixedDelay(3_500).withStatus(200)));
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();

        long debut = System.nanoTime();
        postuler(offreId, etudiant).andExpect(status().isServiceUnavailable());

        assertThat((System.nanoTime() - debut) / 1_000_000).as("durée bornée (ms)").isBetween(3_000L, 9_000L);
        FauxOffreService.SERVEUR.verify(2, getRequestedFor(urlPathEqualTo("/internal/offres/" + offreId)));
    }

    @Test
    void postuler_offreInconnue_404QuiNOuvrePasLeCircuit() throws Exception {
        long offreId = ApiDeTest.nouvelId();
        FauxOffreService.SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres/" + offreId)).willReturn(notFound()));
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();

        for (int i = 0; i < 6; i++) {
            postuler(offreId, etudiant)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("OffreNotFound"));
        }
        // Un 404 est une réponse normale (ignore-exceptions) : il ne compte pas comme un échec
        assertThat(circuitBreakers.getAllCircuitBreakers())
                .allSatisfy(circuit -> assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
    }

    @Test
    void circuitOuvert_lesAppelsEchouentAussitotSansSolliciterOffreService() throws Exception {
        long offreId = ApiDeTest.nouvelId();
        String url = "/internal/offres/" + offreId;
        FauxOffreService.SERVEUR.stubFor(get(urlPathEqualTo(url)).willReturn(serverError()));
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();

        // 5 échecs sur 5 appels (minimum-number-of-calls) : le circuit s'ouvre
        for (int i = 0; i < 5; i++) {
            postuler(offreId, etudiant).andExpect(status().isServiceUnavailable());
        }
        FauxOffreService.SERVEUR.verify(5, getRequestedFor(urlPathEqualTo(url)));

        // offre-service est rétabli, mais le circuit est ouvert (15 s) : refus immédiat, aucun appel réseau
        FauxOffreService.offreOuverte(offreId, ApiDeTest.nouvelId());
        postuler(offreId, etudiant).andExpect(status().isServiceUnavailable());
        FauxOffreService.SERVEUR.verify(5, getRequestedFor(urlPathEqualTo(url)));

        // Circuit refermé (ici à la main, en production après l'attente et 3 appels d'essai réussis) : ça repart
        refermerLesCircuits();
        postuler(offreId, etudiant).andExpect(status().isCreated());
    }

    @Test
    void mesCandidatures_offreServiceEnPanne_listeAvecLesDonneesRecopiees() throws Exception {
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();
        FauxOffreService.SERVEUR.stubFor(get(urlPathEqualTo("/internal/offres"))
                .withQueryParam("ids", equalTo(s.offreId().toString()))
                .willReturn(serverError()));

        api.lire("/api/v1/etudiant/candidatures", s.etudiant().token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(s.candidatureId().intValue()))
                .andExpect(jsonPath("$.content[0].offre.titre").value("Stage PFE Java"))
                .andExpect(jsonPath("$.content[0].offre.entrepriseNom").value("Acme"))
                // Statut de l'offre inconnu : on ne sait pas si elle a été fermée depuis
                .andExpect(jsonPath("$.content[0].offre.statut").doesNotExist());
    }

    private ResultActions postuler(long offreId, ApiDeTest.Compte etudiant) throws Exception {
        return api.appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), etudiant.token(), null);
    }
}
