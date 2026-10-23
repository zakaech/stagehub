package com.stagehub.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void lAnnuaireRepondEtLApplicationEstEnBonneSante() throws Exception {
        HttpResponse<String> annuaire = get("/eureka/apps", "application/json");
        assertThat(annuaire.statusCode()).isEqualTo(200);
        assertThat(annuaire.body()).contains("\"applications\"");

        HttpResponse<String> sante = get("/actuator/health", "application/json");
        assertThat(sante.statusCode()).isEqualTo(200);
        assertThat(sante.body()).contains("\"UP\"");
    }

    private HttpResponse<String> get(String chemin, String accept) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .header("Accept", accept)
                .build();
        return http.send(requete, HttpResponse.BodyHandlers.ofString());
    }
}
