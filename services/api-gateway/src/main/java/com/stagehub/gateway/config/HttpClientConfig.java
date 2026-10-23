package com.stagehub.gateway.config;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client HTTP (Apache HttpClient 5) avec lequel la gateway relaie les requêtes vers les services.
 * <p>
 * Par défaut, HttpClient rejoue une requête, quelle que soit sa méthode (POST compris), quand le service répond
 * 503 ou 429 : la gateway retentait ainsi une candidature refusée faute d'offre-service (en panne), doublant
 * le temps d'attente. Une gateway ne doit pas décider seule de rejouer une écriture : le 503 est relayé tel quel au
 * client, qui choisit de réessayer. Reste une seule nouvelle tentative sur erreur réseau (connexion coupée avant toute
 * réponse), et seulement pour les méthodes idempotentes (GET, PUT, DELETE...) : règle par défaut d'HttpClient.
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> pasDeRejeuSurReponse() {
        return builder -> builder.withHttpClientCustomizer(client -> client.setRetryStrategy(new RejeuSurErreurReseau()));
    }

    static class RejeuSurErreurReseau extends DefaultHttpRequestRetryStrategy {

        RejeuSurErreurReseau() {
            super(1, TimeValue.ofMilliseconds(200));
        }

        /** Le service a répondu (même 503) : la réponse est relayée, jamais rejouée. */
        @Override
        public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
            return false;
        }
    }
}
