package com.stagehub.auth.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.stagehub.auth.ClesDeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La clé de signature est validée au démarrage : une mauvaise configuration empêche auth-service de démarrer. */
class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void cleValide_kidCalculeEtClePubliqueDeduite() throws Exception {
        RSAKey cle = config.cleDeSignature(proprietes(ClesDeTest.clePriveeBase64(), null));

        assertThat(cle.isPrivate()).isTrue();
        assertThat(cle.getKeyID()).isEqualTo(cle.computeThumbprint().toString());
        assertThat(cle.toPublicJWK().isPrivate()).isFalse();
        assertThat(cle.size()).isEqualTo(2048);
    }

    @Test
    void kidFourni_etPemAccepte() throws Exception {
        String base64 = ClesDeTest.clePriveeBase64();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64.replaceAll("(.{64})", "$1\n") + "\n-----END PRIVATE KEY-----\n";

        RSAKey cle = config.cleDeSignature(proprietes(pem, "cle-2026"));

        assertThat(cle.getKeyID()).isEqualTo("cle-2026");
    }

    @Test
    void niCleNiFichier_demarrageRefuseAvecLaCommandeDeGeneration() {
        assertThatThrownBy(() -> new JwtProperties("  ", Path.of(""), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openssl genpkey")
                .hasMessageContaining("JWT_KEY_FILE");
    }

    @Test
    void sansCleFournie_genereeDansLeFichierPuisRelue(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("cles/jwt.key");
        JwtProperties sansCle = new JwtProperties(null, fichier, null, null, null);

        RSAKey premiere = config.cleDeSignature(sansCle);
        RSAKey apresRedemarrage = config.cleDeSignature(sansCle);

        assertThat(fichier).exists();
        assertThat(apresRedemarrage.getKeyID()).isEqualTo(premiere.getKeyID());
        assertThat(apresRedemarrage.toRSAPublicKey()).isEqualTo(premiere.toRSAPublicKey());
    }

    @Test
    void cleFournie_prioritaireSurLeFichier(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("jwt.key");

        config.cleDeSignature(new JwtProperties(ClesDeTest.clePriveeBase64(), fichier, null, null, null));

        assertThat(fichier).doesNotExist();
    }

    @Test
    void cleTropCourteOuIllisible_demarrageRefuse() {
        assertThatThrownBy(() -> config.cleDeSignature(proprietes(ClesDeTest.clePriveeBase64(1024), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048 minimum");
        assertThatThrownBy(() -> config.cleDeSignature(proprietes("pas du base64 !", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> config.cleDeSignature(proprietes(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#8");
    }

    /** Ce que produit « openssl genpkey -outform DER » avec OpenSSL 3 : la clé RSA nue (PKCS#1), sans l'en-tête PKCS#8. */
    @Test
    void clePkcs1_messageQuiDonneLaConversion() {
        byte[] pkcs8 = Base64.getDecoder().decode(ClesDeTest.clePriveeBase64());
        // En-tête PKCS#8 d'une clé RSA 2048 : SEQUENCE, version, identifiant d'algorithme, en-tête de l'OCTET STRING
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);

        assertThatThrownBy(() -> config.cleDeSignature(proprietes(Base64.getEncoder().encodeToString(pkcs1), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#1")
                .hasMessageContaining("openssl pkcs8 -topk8");
    }

    private static JwtProperties proprietes(String clePrivee, String kid) {
        return new JwtProperties(clePrivee, null, kid, Duration.ofHours(1), null);
    }
}
