package com.stagehub.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiStageHubTest {

    private static final String INSCRIPTION = "http://api.test/api/v1/auth/register/etudiant";

    private MockRestServiceServer serveur;
    private ApiStageHub api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        api = new ApiStageHub(builder, "http://api.test", Duration.ZERO);
    }

    @Test
    void springSaitCreerLeBean() {
        // Deux constructeurs : sans @Autowired sur le principal, Spring échoue au démarrage (constaté)
        new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(DemoProperties.class, () -> new DemoProperties("http://api.test", "x", "a", "b", null, null))
                .withBean(ApiStageHub.class)
                .run(contexte -> assertThat(contexte).hasNotFailed().hasSingleBean(ApiStageHub.class));
    }

    @Test
    void reponse503_appelRejoueJusquAuSucces() {
        serveur.expect(ExpectedCount.times(2), requestTo(INSCRIPTION)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        serveur.expect(requestTo(INSCRIPTION))
                .andRespond(withSuccess("{\"id\": 42}", MediaType.APPLICATION_JSON));

        long id = api.inscrireEtudiant("sara@etu.demo", "x", "Alaoui", "Sara", "ENSIAS", "GL", "Rabat");

        assertThat(id).isEqualTo(42);
        serveur.verify();
    }

    @Test
    void toujours503_abandonApresLeNombreMaximalDEssais() {
        serveur.expect(ExpectedCount.times(ApiStageHub.ESSAIS_MAX), requestTo(INSCRIPTION))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> api.inscrireEtudiant("sara@etu.demo", "x", "Alaoui", "Sara", "ENSIAS", "GL", "Rabat"))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        serveur.verify();
    }

    @Test
    void autreErreur_pasDeNouvelEssai() {
        serveur.expect(ExpectedCount.once(), requestTo(INSCRIPTION))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> api.inscrireEtudiant("sara@etu.demo", "x", "Alaoui", "Sara", "ENSIAS", "GL", "Rabat"))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        serveur.verify();
    }
}
