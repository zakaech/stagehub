package com.stagehub.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFichierStorageTest {

    @TempDir
    Path dossier;

    private LocalFichierStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFichierStorage(dossier);
    }

    @Test
    void enregistrer_utiliseUnNomUuidEtConserveLeContenu() throws Exception {
        String nom = storage.enregistrer(new ByteArrayInputStream("contenu".getBytes()), ".pdf");

        assertThat(nom).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        assertThat(Files.readString(dossier.resolve(nom))).isEqualTo("contenu");
        // Aucun fichier temporaire ne traîne après l'écriture
        try (var fichiers = Files.list(dossier)) {
            assertThat(fichiers).hasSize(1);
        }
    }

    @Test
    void charger_etSupprimer() {
        String nom = storage.enregistrer(new ByteArrayInputStream("x".getBytes()), ".pdf");

        assertThat(storage.charger(nom)).isPresent();
        storage.supprimer(nom);
        assertThat(storage.charger(nom)).isEmpty();
    }

    @Test
    void cheminSortantDuDossier_estRefuse() {
        assertThatThrownBy(() -> storage.charger("../secret.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.supprimer("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.charger("sous-dossier/cv.pdf")).isInstanceOf(IllegalArgumentException.class);
    }
}
