package com.stagehub.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Appels à l'API publique de StageHub (à travers la gateway), avec les mêmes URL que le frontend.
 * <p>
 * Un appel qui reçoit 503 est rejoué (toutes les 5 s, pendant une minute au plus). Dans StageHub, 503 signifie
 * « l'action n'a pas eu lieu, réessayez » : instance pas encore trouvée par la gateway, offre illisible pour postuler,
 * saga d'inscription compensée. Limite : si la compensation n'a pas pu joindre le service des profils, le compte
 * reste EN_ATTENTE et un nouvel essai reçoit 409 ; d'où l'attente des services avant de commencer (DemoDataSeeder).
 */
@Component
public class ApiStageHub {

    private static final Logger log = LoggerFactory.getLogger(ApiStageHub.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };
    static final int ESSAIS_MAX = 12;

    private final RestClient http;
    private final Duration pauseEntreEssais;

    /** Constructeur utilisé par Spring (annoté, car il y en a deux : l'autre sert aux tests). */
    @Autowired
    public ApiStageHub(RestClient.Builder builder, DemoProperties properties) {
        this(builder, properties.apiUrl(), Duration.ofSeconds(5));
    }

    /** Pause réglable pour les tests. */
    ApiStageHub(RestClient.Builder builder, String apiUrl, Duration pauseEntreEssais) {
        this.http = builder.baseUrl(apiUrl).build();
        this.pauseEntreEssais = pauseEntreEssais;
    }

    /**
     * L'API répond-elle de bout en bout ? (gateway → offre-service pour les offres, gateway → auth-service pour la
     * connexion ; juste après le démarrage, la gateway peut encore répondre 503 le temps de les trouver dans Eureka.)
     */
    public boolean prete() {
        try {
            http.get().uri("/api/v1/offres?size=1").retrieve().toBodilessEntity();
            connecter("sonde@demo.invalid", "pas-un-mot-de-passe");
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /**
     * Les services métier répondent-ils à travers la gateway ? Statistiques admin d'offre-service et de
     * candidature-service : sans cela, une inscription pourrait échouer faute de service des profils joignable.
     */
    public boolean servicesPrets(String jetonAdmin) {
        try {
            for (String uri : List.of("/api/v1/admin/stats/offres", "/api/v1/admin/stats/candidatures")) {
                http.get().uri(uri).header("Authorization", "Bearer " + jetonAdmin).retrieve().toBodilessEntity();
            }
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /** Jeton si les identifiants sont bons, vide s'ils sont refusés (401). */
    public Optional<String> connecter(String email, String motDePasse) {
        try {
            Map<String, Object> reponse = http.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "motDePasse", motDePasse))
                    .retrieve().body(JSON);
            return Optional.of((String) reponse.get("accessToken"));
        } catch (HttpClientErrorException.Unauthorized e) {
            return Optional.empty();
        }
    }

    public long inscrireEntreprise(String email, String motDePasse, String nom, String secteur, String ville,
                                   String description) {
        return avecNouvellesTentatives(() -> id(http.post().uri("/api/v1/auth/register/entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "motDePasse", motDePasse, "nom", nom, "secteur", secteur,
                        "ville", ville, "description", description))
                .retrieve().body(JSON)));
    }

    public long inscrireEtudiant(String email, String motDePasse, String nom, String prenom, String ecole,
                                 String filiere, String ville) {
        return avecNouvellesTentatives(() -> id(http.post().uri("/api/v1/auth/register/etudiant")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "motDePasse", motDePasse, "nom", nom, "prenom", prenom,
                        "ecole", ecole, "filiere", filiere, "ville", ville))
                .retrieve().body(JSON)));
    }

    public void validerEntreprise(String jetonAdmin, long entrepriseId) {
        avecNouvellesTentatives(() -> http.patch().uri("/api/v1/admin/entreprises/{id}/validation", entrepriseId)
                .header("Authorization", "Bearer " + jetonAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("decision", "VALIDEE"))
                .retrieve().toBodilessEntity());
    }

    public long creerOffre(String jeton, String titre, String description, String ville, String domaine,
                           List<String> technologies, int dureeMois, LocalDate dateLimite) {
        return avecNouvellesTentatives(() -> id(http.post().uri("/api/v1/entreprise/offres")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("titre", titre, "description", description, "ville", ville, "domaine", domaine,
                        "technologies", technologies, "dureeMois", dureeMois, "dateLimite", dateLimite.toString()))
                .retrieve().body(JSON)));
    }

    public void fermerOffre(String jeton, long offreId) {
        avecNouvellesTentatives(() -> http.patch().uri("/api/v1/entreprise/offres/{id}/statut", offreId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("statut", "FERMEE"))
                .retrieve().toBodilessEntity());
    }

    /** Lettre de motivation facultative (null : aucune). */
    public long postuler(String jeton, long offreId, String lettre) {
        Map<String, Object> corps = lettre == null ? Map.of() : Map.of("lettreMotivation", lettre);
        return avecNouvellesTentatives(() -> id(http.post().uri("/api/v1/etudiant/offres/{id}/candidatures", offreId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corps)
                .retrieve().body(JSON)));
    }

    public void changerStatut(String jeton, long candidatureId, String statut) {
        avecNouvellesTentatives(() -> http.patch().uri("/api/v1/entreprise/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("statut", statut))
                .retrieve().toBodilessEntity());
    }

    /** Rejoue l'appel tant qu'il reçoit 503, au plus {@link #ESSAIS_MAX} fois ; toute autre erreur est propagée. */
    private <T> T avecNouvellesTentatives(Supplier<T> appel) {
        for (int essai = 1; ; essai++) {
            try {
                return appel.get();
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                if (essai == ESSAIS_MAX) {
                    throw e;
                }
                log.info("Service momentanément indisponible (503), nouvel essai dans {} s ({}/{})",
                        pauseEntreEssais.toSeconds(), essai, ESSAIS_MAX);
                try {
                    Thread.sleep(pauseEntreEssais);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static long id(Map<String, Object> reponse) {
        return ((Number) reponse.get("id")).longValue();
    }
}
