package com.stagehub.offre.controller;

import com.stagehub.common.exception.EntrepriseNonValideeException;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.offre.dto.OffreRequest;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.service.OffreService;
import com.stagehub.commons.security.ProblemDetailSecurityHandler;
import com.stagehub.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tranche web (@WebMvcTest) : seuls le controller, la sécurité et le @RestControllerAdvice sont chargés ;
 * le service est simulé. Rapide, sans base de données : on teste le contrat HTTP (codes, validation,
 * ProblemDetail, rôles). Les JWT sont fabriqués par spring-security-test (jwt()), sans signature réelle.
 */
@WebMvcTest(EntrepriseOffreController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class EntrepriseOffreControllerTest {

    private static final long ENTREPRISE_ID = 42L;
    private static final String OFFRE_VALIDE = """
            {"titre": "Stage PFE Java", "description": "API Spring", "ville": "Rabat", "domaine": "Dev",
             "technologies": ["Java"], "dureeMois": 6, "dateLimite": "%s"}
            """.formatted(LocalDate.now().plusDays(30));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OffreService offreService;
    // Requis par le resource server (SecurityConfig) ; non utilisé grâce à jwt()
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor connecteEn(String role) {
        return jwt().jwt(j -> j.claim("uid", ENTREPRISE_ID).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void sansJeton_401AuFormatProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/entreprise/offres").contentType(MediaType.APPLICATION_JSON).content(OFFRE_VALIDE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NonAuthentifie"));
        verifyNoInteractions(offreService);
    }

    @Test
    void roleEtudiant_403() throws Exception {
        mockMvc.perform(post("/api/v1/entreprise/offres").with(connecteEn("ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(OFFRE_VALIDE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AccesRefuse"));
        verifyNoInteractions(offreService);
    }

    @Test
    void offreInvalide_400AvecLaListeDesChamps() throws Exception {
        mockMvc.perform(post("/api/v1/entreprise/offres").with(connecteEn("ENTREPRISE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titre\": \"\", \"dureeMois\": 13}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationEchouee"))
                .andExpect(jsonPath("$.erreurs[*].champ", hasItems("titre", "dureeMois", "technologies", "dateLimite")));
        verifyNoInteractions(offreService);
    }

    @Test
    void creer_201AvecLocationEtIdDeLEntrepriseLuDansLeJeton() throws Exception {
        when(offreService.creer(eq(ENTREPRISE_ID), any(OffreRequest.class))).thenReturn(new OffreResponse(
                7L, "Stage PFE Java", "API Spring", "Rabat", "Dev", Set.of("Java"), 6,
                LocalDate.now().plusDays(30), StatutOffre.OUVERTE, ENTREPRISE_ID, "Acme"));

        mockMvc.perform(post("/api/v1/entreprise/offres").with(connecteEn("ENTREPRISE"))
                        .contentType(MediaType.APPLICATION_JSON).content(OFFRE_VALIDE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/entreprise/offres/7"))
                .andExpect(jsonPath("$.id").value(7));
        verify(offreService).creer(eq(ENTREPRISE_ID), any(OffreRequest.class));
    }

    @Test
    void exceptionMetier_traduiteEnProblemDetail() throws Exception {
        when(offreService.creer(eq(ENTREPRISE_ID), any(OffreRequest.class)))
                .thenThrow(new EntrepriseNonValideeException(StatutValidation.EN_ATTENTE));

        mockMvc.perform(post("/api/v1/entreprise/offres").with(connecteEn("ENTREPRISE"))
                        .contentType(MediaType.APPLICATION_JSON).content(OFFRE_VALIDE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EntrepriseNonValidee"))
                .andExpect(jsonPath("$.detail").value(containsString("en attente de validation")));
    }

    @Test
    void statutInconnu_400CorpsIllisible() throws Exception {
        mockMvc.perform(patch("/api/v1/entreprise/offres/7/statut").with(connecteEn("ENTREPRISE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statut\": \"ARCHIVEE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CorpsIllisible"));
    }
}
