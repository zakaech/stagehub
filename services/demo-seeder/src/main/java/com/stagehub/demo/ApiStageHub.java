package com.stagehub.demo;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Appels à l'API publique de StageHub (à travers la gateway), avec les mêmes URL que le frontend. */
@Component
public class ApiStageHub {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    public ApiStageHub(RestClient.Builder builder, DemoProperties properties) {
        this.http = builder.baseUrl(properties.apiUrl()).build();
    }

    /**
     * L'API répond-elle de bout en bout ? (gateway → monolithe pour les offres, gateway → auth-service pour la
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
        return id(http.post().uri("/api/v1/auth/register/entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "motDePasse", motDePasse, "nom", nom, "secteur", secteur,
                        "ville", ville, "description", description))
                .retrieve().body(JSON));
    }

    public long inscrireEtudiant(String email, String motDePasse, String nom, String prenom, String ecole,
                                 String filiere, String ville) {
        return id(http.post().uri("/api/v1/auth/register/etudiant")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "motDePasse", motDePasse, "nom", nom, "prenom", prenom,
                        "ecole", ecole, "filiere", filiere, "ville", ville))
                .retrieve().body(JSON));
    }

    public void validerEntreprise(String jetonAdmin, long entrepriseId) {
        http.patch().uri("/api/v1/admin/entreprises/{id}/validation", entrepriseId)
                .header("Authorization", "Bearer " + jetonAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("decision", "VALIDEE"))
                .retrieve().toBodilessEntity();
    }

    public long creerOffre(String jeton, String titre, String description, String ville, String domaine,
                           List<String> technologies, int dureeMois, LocalDate dateLimite) {
        return id(http.post().uri("/api/v1/entreprise/offres")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("titre", titre, "description", description, "ville", ville, "domaine", domaine,
                        "technologies", technologies, "dureeMois", dureeMois, "dateLimite", dateLimite.toString()))
                .retrieve().body(JSON));
    }

    public void fermerOffre(String jeton, long offreId) {
        http.patch().uri("/api/v1/entreprise/offres/{id}/statut", offreId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("statut", "FERMEE"))
                .retrieve().toBodilessEntity();
    }

    /** Lettre de motivation facultative (null : aucune). */
    public long postuler(String jeton, long offreId, String lettre) {
        Map<String, Object> corps = lettre == null ? Map.of() : Map.of("lettreMotivation", lettre);
        return id(http.post().uri("/api/v1/etudiant/offres/{id}/candidatures", offreId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corps)
                .retrieve().body(JSON));
    }

    public void changerStatut(String jeton, long candidatureId, String statut) {
        http.patch().uri("/api/v1/entreprise/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("statut", statut))
                .retrieve().toBodilessEntity();
    }

    private static long id(Map<String, Object> reponse) {
        return ((Number) reponse.get("id")).longValue();
    }
}
