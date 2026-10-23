package com.stagehub.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Clés RSA générées pour les tests (jamais de vraie clé dans le dépôt). */
public final class ClesDeTest {

    private static final String CLE_PRIVEE_2048 = clePriveeBase64(2048);

    private ClesDeTest() {
    }

    /** Clé privée RSA 2048 bits au format attendu par JWT_PRIVATE_KEY (PKCS#8 en Base64). */
    public static String clePriveeBase64() {
        return CLE_PRIVEE_2048;
    }

    public static String clePriveeBase64(int bits) {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("RSA");
            generateur.initialize(bits);
            KeyPair paire = generateur.generateKeyPair();
            return Base64.getEncoder().encodeToString(paire.getPrivate().getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
