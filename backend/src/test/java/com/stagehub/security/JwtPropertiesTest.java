package com.stagehub.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void secretDe31Octets_estRefuse() {
        assertThatThrownBy(() -> new JwtProperties("a".repeat(31), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au moins 32 octets")
                .hasMessageContaining("31 octets");
    }

    @Test
    void secretAbsent_estRefuse() {
        assertThatThrownBy(() -> new JwtProperties(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretDe32Octets_estAccepteAvecValeursParDefaut() {
        JwtProperties properties = new JwtProperties("a".repeat(32), null, null);

        assertThat(properties.expiration()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.issuer()).isEqualTo("stagehub");
    }

    @Test
    void laTailleSeCompteEnOctetsPasEnCaracteres() {
        // "é" occupe 2 octets en UTF-8 : 16 caractères = 32 octets, donc accepté
        assertThat(new JwtProperties("é".repeat(16), null, null).secretEnOctets()).hasSize(32);
    }
}
