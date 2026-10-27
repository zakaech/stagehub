package com.stagehub.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Vérifie l'identifiant et le secret d'un service qui demande un jeton (flux client credentials).
 * <ul>
 *   <li>un secret vide ou trop court (moins de 32 caractères) n'est pas retenu : ce client ne peut obtenir aucun
 *       jeton (on refuse plutôt que d'accepter un secret faible) ;</li>
 *   <li>la comparaison est faite en temps constant ({@link MessageDigest#isEqual}) : le temps de réponse ne révèle
 *       pas combien de caractères du secret proposé sont justes.</li>
 * </ul>
 */
@Component
public class ClientsService {

    static final int LONGUEUR_MIN_SECRET = 32;
    private static final Logger log = LoggerFactory.getLogger(ClientsService.class);

    private final Map<String, byte[]> secrets = new HashMap<>();

    public ClientsService(ClientsServiceProperties properties) {
        properties.secrets().forEach((client, secret) -> {
            if (secret == null || secret.length() < LONGUEUR_MIN_SECRET) {
                log.warn("Secret du client « {} » absent ou trop court (moins de {} caractères) : "
                        + "ce service ne pourra pas obtenir de jeton", client, LONGUEUR_MIN_SECRET);
            } else {
                secrets.put(client, secret.getBytes(StandardCharsets.UTF_8));
            }
        });
        log.info("Clients autorisés à demander un jeton de service : {}", secrets.keySet());
    }

    /** Vrai si le client est connu et que son secret est le bon. */
    public boolean authentifier(String client, String secret) {
        byte[] attendu = client == null ? null : secrets.get(client);
        if (attendu == null || secret == null) {
            return false;
        }
        return MessageDigest.isEqual(attendu, secret.getBytes(StandardCharsets.UTF_8));
    }
}
