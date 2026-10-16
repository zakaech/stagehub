package com.stagehub.cv;

import com.jayway.jsonpath.JsonPath;
import com.stagehub.IntegrationTestBase;
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
import java.time.LocalDate;
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
        String token = inscrireEtudiant("sans-cv-" + suffixe());

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
        String autreEntreprise = inscrireEntreprise("autre-" + suffixe());

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
        String token = inscrireEtudiant("remplace-" + suffixe());
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
        String token = inscrireEtudiant("depot-" + suffixe());
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

    private Scenario scenario() throws Exception {
        String s = suffixe();
        String tokenEntreprise = inscrireEntreprise("rh-" + s);
        Long entrepriseId = idDe(appeler(get("/api/v1/auth/me"), tokenEntreprise));
        String tokenAdmin = connecter("admin@test.local", "admin-test-1234");
        appeler(patch("/api/v1/admin/entreprises/{id}/validation", entrepriseId), tokenAdmin, "{\"decision\": \"VALIDEE\"}")
                .andExpect(status().isOk());
        Long offreId = idDe(appeler(post("/api/v1/entreprise/offres"), tokenEntreprise, """
                {"titre": "Stage CV", "description": "Test", "ville": "Rabat", "domaine": "Dev",
                 "technologies": ["Java"], "dureeMois": 4, "dateLimite": "%s"}
                """.formatted(LocalDate.now().plusDays(30))).andExpect(status().isCreated()));

        String tokenEtudiant = inscrireEtudiant("etu-" + s);
        Long etudiantId = idDe(deposer(tokenEtudiant, CV_1).andExpect(status().isOk()));
        Long candidatureId = idDe(appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant)
                .andExpect(status().isCreated()));
        return new Scenario(tokenEntreprise, tokenEtudiant, etudiantId, candidatureId);
    }

    private String inscrireEtudiant(String identifiant) throws Exception {
        String email = identifiant + "@cv.test";
        appeler(post("/api/v1/auth/register/etudiant"), null, """
                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "GL", "ville": "Rabat"}
                """.formatted(email)).andExpect(status().isCreated());
        return connecter(email, "motdepasse1");
    }

    private String inscrireEntreprise(String identifiant) throws Exception {
        String email = identifiant + "@cv.test";
        appeler(post("/api/v1/auth/register/entreprise"), null, """
                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Entreprise %s", "secteur": "IT", "ville": "Rabat"}
                """.formatted(email, identifiant)).andExpect(status().isCreated());
        return connecter(email, "motdepasse1");
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

    private String connecter(String email, String motDePasse) throws Exception {
        String json = appeler(post("/api/v1/auth/login"), null,
                "{\"email\": \"%s\", \"motDePasse\": \"%s\"}".formatted(email, motDePasse))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.accessToken");
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
