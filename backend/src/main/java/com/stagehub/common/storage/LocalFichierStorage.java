package com.stagehub.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** Fichiers rangés à plat dans un dossier, sous un nom UUID. */
public class LocalFichierStorage implements FichierStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFichierStorage.class);

    private final Path racine;

    public LocalFichierStorage(Path dossier) {
        this.racine = dossier.toAbsolutePath().normalize();
        try {
            Files.createDirectories(racine);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de stockage " + racine, e);
        }
    }

    @Override
    public String enregistrer(InputStream contenu, String extension) {
        String nom = UUID.randomUUID() + extension;
        Path cible = resoudre(nom);
        Path temporaire = null;
        try {
            // Écriture dans un fichier temporaire puis déplacement atomique :
            // un lecteur ne voit jamais un fichier à moitié écrit
            temporaire = Files.createTempFile(racine, "upload-", ".tmp");
            Files.copy(contenu, temporaire, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporaire, cible, StandardCopyOption.ATOMIC_MOVE);
            return nom;
        } catch (IOException e) {
            supprimerSansErreur(temporaire);
            throw new UncheckedIOException("Échec de l'enregistrement du fichier", e);
        }
    }

    @Override
    public Optional<Resource> charger(String nom) {
        Path fichier = resoudre(nom);
        return Files.isRegularFile(fichier) ? Optional.of(new FileSystemResource(fichier)) : Optional.empty();
    }

    @Override
    public void supprimer(String nom) {
        supprimerSansErreur(resoudre(nom));
    }

    /**
     * Défense en profondeur contre le path traversal : le chemin final doit être directement dans la racine.
     * "../../etc/passwd" ou "sous/dossier.pdf" sont refusés, même si les noms viennent normalement de la base.
     */
    Path resoudre(String nom) {
        Path chemin = racine.resolve(nom).normalize();
        if (!racine.equals(chemin.getParent())) {
            throw new IllegalArgumentException("Nom de fichier invalide : " + nom);
        }
        return chemin;
    }

    private static void supprimerSansErreur(Path fichier) {
        if (fichier == null) {
            return;
        }
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            // Un fichier orphelin ne doit pas faire échouer l'opération métier ; on le signale seulement
            log.warn("Impossible de supprimer le fichier {}", fichier, e);
        }
    }
}
