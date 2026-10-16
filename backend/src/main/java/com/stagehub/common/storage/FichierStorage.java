package com.stagehub.common.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Stockage de fichiers, indépendant du support. L'implémentation actuelle écrit sur disque
 * ({@link LocalFichierStorage}) ; un stockage objet (S3, MinIO) pourrait la remplacer sans toucher au métier.
 */
public interface FichierStorage {

    /** Enregistre le contenu sous un nom généré (jamais celui fourni par l'utilisateur) et renvoie ce nom. */
    String enregistrer(InputStream contenu, String extension);

    Optional<Resource> charger(String nom);

    void supprimer(String nom);
}
