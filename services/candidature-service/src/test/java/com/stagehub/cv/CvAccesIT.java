package com.stagehub.cv;

import com.jayway.jsonpath.JsonPath;
import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.JetonsDeTest;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.cv.config.CvProperties;
import com.stagehub.etudiant.repository.EtudiantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Règles d'accès au CV et cycle de vie des fichiers, de bout en bout (HTTP → service → PostgreSQL → disque).
 * Chaque test crée ses propres comptes (suffixe aléatoire) : les tests sont indépendants les uns des autres.
 */
class CvAccesIT extends IntegrationTestBase {

    private static final byte[] CV_1 = "%PDF-1.7\n% premier CV\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CV_2 = "%PDF-1.7\n% second CV\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private CvProperties cvProperties;
    @Autowired
    private EtudiantRepository etudiantRepository;
    @Autowired
    private CandidatureRepository candidatureRepository;

    /** Une entreprise validée avec une offre, un étudiant qui a déposé CV_1 puis postulé. */
    private record Scenario(String tokenEntreprise, String tokenEtudiant, Long etudiantId, Long candidatureId) {
    }

    // --- Téléchargement : règles d'accès ---

    @Test
    void etudiant_telechargeSonPropreCv() throws Exception {
        Scenario s = scenario();

        appeler(get("/api/v1/etudiant/cv"), s.tokenEtudiant())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().bytes(CV_1));
    }

    @Test
    void etudiantSansCv_recoit404() throws Exception {
        String token = inscrireEtudiant();

        appeler(get("/api/v1/etudiant/cv"), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CvNotFound"));
    }

    @Test
    void entrepriseDeLOffre_telechargeLeCvDeLaCandidature() throws Exception {
        Scenario s = scenario();

        appeler(get("/api/v1/entreprise/candidatures/{id}/cv", s.candidatureId()), s.tokenEntreprise())
                .andExpect(status().isOk())
                .andExpect(content().bytes(CV_1));
    }

    @Test
    void autreEntreprise_recoit403() throws Exception {
        Scenario s = scenario();
        String autreEntreprise = autreEntreprise();

        appeler(get("/api/v1/entreprise/candidatures/{id}/cv", s.candidatureId()), autreEntreprise)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AccesNonAutorise"));
    }

    @Test
    void etudiantSurLEndpointEntreprise_recoit403_etVisiteur401() throws Exception {
        Scenario s = scenario();

        appeler(get("/api/v1/entreprise/candidatures/{id}/cv", s.candidatureId()), s.tokenEtudiant())
                .andExpect(status().isForbidden());
        appeler(get("/api/v1/entreprise/candidatures/{id}/cv", s.candidatureId()), null)
                .andExpect(status().isUnauthorized());
        appeler(get("/api/v1/etudiant/cv"), null)
                .andExpect(status().isUnauthorized());
    }

    // --- CV figé dans la candidature ---

    @Test
    void cvRemplaceApresCandidature_lEntrepriseGardeLeCvFige() throws Exception {
        Scenario s = scenario();
        String cvFige = etudiantRepository.findById(s.etudiantId()).orElseThrow().getCheminCv();

        deposer(s.tokenEtudiant(), CV_2).andExpect(status().isOk());

        appeler(get("/api/v1/entreprise/candidatures/{id}/cv", s.candidatureId()), s.tokenEntreprise())
                .andExpect(content().bytes(CV_1));
        appeler(get("/api/v1/etudiant/cv"), s.tokenEtudiant())
                .andExpect(content().bytes(CV_2));
        assertThat(candidatureRepository.existsByCvFichier(cvFige)).isTrue();
        assertThat(Files.exists(fichierCv(cvFige))).as("le CV figé reste sur le disque").isTrue();
    }

    @Test
    void cvRemplaceJamaisUtilise_lAncienFichierEstSupprime() throws Exception {
        String token = inscrireEtudiant();
        Long etudiantId = idDe(deposer(token, CV_1).andExpect(status().isOk()));
        String ancien = etudiantRepository.findById(etudiantId).orElseThrow().getCheminCv();

        deposer(token, CV_2).andExpect(status().isOk());

        assertThat(Files.exists(fichierCv(ancien))).isFalse();
        assertThat(Files.exists(fichierCv(etudiantRepository.findById(etudiantId).orElseThrow().getCheminCv())))
                .isTrue();
    }

    // --- Dépôt : validation du fichier ---

    @Test
    void depot_refuseFauxPdf_fichierTropGros_etFichierVide() throws Exception {
        String token = inscrireEtudiant();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] sixMo = new byte[6 * 1024 * 1024];
        System.arraycopy(CV_1, 0, sixMo, 0, CV_1.length);

        deposer(token, png).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FichierNonPdf"));
        deposer(token, sixMo).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FichierTropVolumineux"));
        deposer(token, new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FichierVide"));
        appeler(get("/api/v1/etudiant/cv"), token).andExpect(status().isNotFound());
    }

    // --- Mise en place ---

    // --- Lecture par ai-service pour l'analyse ---

    @Test
    void aiService_litLeCvFigeDeLaCandidature_sansNomDeFichier() throws Exception {
        Scenario s = scenario();

        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()), JetonsDeTest.service("ai-service"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(CV_1))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void cvPourAnalyse_refuseAuxAutresJetons() throws Exception {
        Scenario s = scenario();
        ApiDeTest api = new ApiDeTest(mockMvc);

        // Rôle SERVICE mais autre service (auth-service) : 403
        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()), JetonsDeTest.service())
                .andExpect(status().isForbidden());
        // Sujet « ai-service » sans le rôle SERVICE (jeton d'utilisateur) : 403
        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()),
                JetonsDeTest.utilisateur(1L, "ai-service", "ADMIN"))
                .andExpect(status().isForbidden());
        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()), api.tokenAdmin())
                .andExpect(status().isForbidden());
        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()), s.tokenEntreprise())
                .andExpect(status().isForbidden());
        appeler(get("/internal/candidatures/{id}/cv", s.candidatureId()), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cvPourAnalyse_candidatureInconnueOuSansCv_404() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        Long sansCv = api.scenarioAvecCandidature().candidatureId();

        appeler(get("/internal/candidatures/{id}/cv", 999_999_999L), JetonsDeTest.service("ai-service"))
                .andExpect(status().isNotFound());
        appeler(get("/internal/candidatures/{id}/cv", sansCv), JetonsDeTest.service("ai-service"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CvNotFound"));
    }

    private Scenario scenario() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.entreprise();
        String tokenEntreprise = entreprise.token();
        Long offreId = api.offreOuverte(entreprise);

        String tokenEtudiant = inscrireEtudiant();
        Long etudiantId = idDe(deposer(tokenEtudiant, CV_1).andExpect(status().isOk()));
        Long candidatureId = idDe(appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant)
                .andExpect(status().isCreated()));
        return new Scenario(tokenEntreprise, tokenEtudiant, etudiantId, candidatureId);
    }

    /** Profils créés comme le fait auth-service à l'inscription (voir ApiDeTest). */
    private String inscrireEtudiant() throws Exception {
        return new ApiDeTest(mockMvc).inscrireEtudiant().token();
    }

    /** Entreprise (dans offre-service) : seulement un jeton ici. */
    private String autreEntreprise() {
        return new ApiDeTest(mockMvc).entreprise().token();
    }

    private ResultActions deposer(String token, byte[] contenu) throws Exception {
        // Nom et type annoncés "corrects" : seul le contenu doit décider
        MockMultipartFile fichier = new MockMultipartFile("fichier", "cv.pdf", "application/pdf", contenu);
        return mockMvc.perform(multipart("/api/v1/etudiant/cv").file(fichier)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions appeler(MockHttpServletRequestBuilder requete, String token) throws Exception {
        return appeler(requete, token, null);
    }

    private ResultActions appeler(MockHttpServletRequestBuilder requete, String token, String corps) throws Exception {
        if (token != null) {
            requete.header("Authorization", "Bearer " + token);
        }
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mockMvc.perform(requete);
    }

    private static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Path fichierCv(String nom) {
        return cvProperties.dossier().resolve(nom);
    }

    private static String suffixe() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
