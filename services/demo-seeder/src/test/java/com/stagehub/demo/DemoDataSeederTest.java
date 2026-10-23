package com.stagehub.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ApiStageHub api;

    private final DemoProperties properties =
            new DemoProperties("http://gateway", "demo1234", "admin@test.local", "admin-1234", Duration.ZERO, Duration.ZERO);

    @Test
    void donneesDejaPresentes_rienNEstCree() throws Exception {
        when(api.prete()).thenReturn(true);
        when(api.connecter(DemoDataSeeder.COMPTE_TEMOIN, "demo1234")).thenReturn(Optional.of("jeton"));

        new DemoDataSeeder(api, properties, HORLOGE).run(null);

        verify(api, never()).inscrireEntreprise(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(api, never()).inscrireEtudiant(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void baseVide_toutesLesDonneesSontCreeesParLApi() throws Exception {
        when(api.prete()).thenReturn(true);
        // Le compte témoin n'existe pas encore au premier essai, puis toutes les connexions réussissent
        when(api.connecter(anyString(), anyString())).thenReturn(Optional.of("jeton"));
        when(api.connecter(DemoDataSeeder.COMPTE_TEMOIN, "demo1234")).thenReturn(Optional.empty(), Optional.of("jeton-tn"));
        when(api.servicesPrets("jeton")).thenReturn(true);
        lenient().when(api.postuler(anyString(), anyLong(), any())).thenReturn(10L);

        new DemoDataSeeder(api, properties, HORLOGE).run(null);

        verify(api, times(3)).inscrireEntreprise(anyString(), eq("demo1234"), anyString(), anyString(), anyString(), anyString());
        verify(api, times(2)).validerEntreprise(eq("jeton"), anyLong());
        verify(api, times(6)).creerOffre(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any());
        verify(api).fermerOffre(eq("jeton"), anyLong());
        verify(api, times(3)).inscrireEtudiant(anyString(), eq("demo1234"), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(api, times(7)).postuler(anyString(), anyLong(), any());
        verify(api, times(3)).postuler(anyString(), anyLong(), isNull());
        // ENTRETIEN ; ENTRETIEN puis ACCEPTEE ; REFUSEE ; ENTRETIEN
        verify(api, times(3)).changerStatut(anyString(), anyLong(), eq("ENTRETIEN"));
        verify(api).changerStatut(anyString(), anyLong(), eq("ACCEPTEE"));
        verify(api).changerStatut(anyString(), anyLong(), eq("REFUSEE"));
    }

    @Test
    void adminInconnu_arretAvecUnMessageClair() {
        when(api.prete()).thenReturn(true);
        when(api.connecter(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DemoDataSeeder(api, properties, HORLOGE).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL");
    }

    @Test
    void servicesMetierJamaisJoignables_arretSansRienCreer() {
        when(api.prete()).thenReturn(true);
        when(api.connecter(anyString(), anyString())).thenReturn(Optional.of("jeton"));
        when(api.connecter(DemoDataSeeder.COMPTE_TEMOIN, "demo1234")).thenReturn(Optional.empty());
        when(api.servicesPrets("jeton")).thenReturn(false);

        assertThatThrownBy(() -> new DemoDataSeeder(api, properties, HORLOGE).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Services injoignables");
        verify(api, never()).inscrireEntreprise(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void apiJamaisPrete_arretApresLeDelai() {
        when(api.prete()).thenReturn(false);

        assertThatThrownBy(() -> new DemoDataSeeder(api, properties, HORLOGE).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injoignable");
    }
}
