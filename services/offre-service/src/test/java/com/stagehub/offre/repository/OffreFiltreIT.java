package com.stagehub.offre.repository;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.dto.OffreFiltre;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtres des offres sur un vrai PostgreSQL (lower(), EXISTS, index). @Transactional : chaque test est annulé
 * à la fin, et les valeurs "zz-filtre..." évitent toute interférence avec les données des autres *IT.
 */
@Transactional
class OffreFiltreIT extends IntegrationTestBase {

    private static final String VILLE = "Zz-Filtre-Rabat";
    private static final String AUTRE_VILLE = "Zz-Filtre-Casa";

    @Autowired
    private OffreRepository offreRepository;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    private Entreprise entreprise;
    private Offre javaSpring;
    private Offre pythonSql;
    private Offre javaPython;

    @BeforeEach
    void creerOffres() {
        entreprise = entreprise();
        javaSpring = offre(entreprise, VILLE, "Zz-Filtre-Dev", StatutOffre.OUVERTE, "zzJava", "zzSpring");
        pythonSql = offre(entreprise, VILLE, "Zz-Filtre-Data", StatutOffre.OUVERTE, "zzPython", "zzSQL");
        javaPython = offre(entreprise, AUTRE_VILLE, "Zz-Filtre-Data", StatutOffre.OUVERTE, "zzJava", "zzPython");
        offre(entreprise, VILLE, "Zz-Filtre-Dev", StatutOffre.FERMEE, "zzJava");
    }

    @Test
    void villeEtDomaine_sontInsensiblesALaCasseEtAuxEspaces() {
        assertThat(rechercher(new OffreFiltre("  zz-FILTRE-rabat ", null, null)))
                .containsExactlyInAnyOrder(javaSpring, pythonSql);
        assertThat(rechercher(new OffreFiltre(null, "ZZ-FILTRE-DATA", null)))
                .containsExactlyInAnyOrder(pythonSql, javaPython);
    }

    @Test
    void technologies_auMoinsUne_sansDoublonEtInsensibleALaCasse() {
        // javaPython correspond aux deux technologies demandées : il ne doit apparaître qu'une fois
        Page<Offre> page = offreRepository.findAll(
                OffreSpecifications.ouvertesCorrespondantA(new OffreFiltre(null, null, List.of("ZZJAVA", "zzpython"))),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactlyInAnyOrder(javaSpring, pythonSql, javaPython);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void criteres_seCumulent() {
        assertThat(rechercher(new OffreFiltre(VILLE, "zz-filtre-data", List.of("zzJava", "zzSQL"))))
                .containsExactly(pythonSql);
        assertThat(rechercher(new OffreFiltre(AUTRE_VILLE, null, List.of("zzSpring")))).isEmpty();
    }

    @Test
    void offresFermees_sontToujoursExclues() {
        assertThat(rechercher(new OffreFiltre(VILLE, "Zz-Filtre-Dev", null))).containsExactly(javaSpring);
    }

    @Test
    void pagination_totalJusteAvecLeFiltreTechnologies() {
        Page<Offre> premierePage = offreRepository.findAll(
                OffreSpecifications.ouvertesCorrespondantA(new OffreFiltre(null, null, List.of("zzJava", "zzPython"))),
                PageRequest.of(0, 2, Sort.by("id")));

        assertThat(premierePage.getContent()).hasSize(2);
        assertThat(premierePage.getTotalElements()).isEqualTo(3);
        assertThat(premierePage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void filtresDisponibles_dedoublonnentSansTenirCompteDeLaCasse() {
        offre(entreprise, "zz-filtre-rabat", "Zz-Filtre-Dev", StatutOffre.OUVERTE, "ZZJAVA");

        List<String> villes = offreRepository.villesDistinctes(StatutOffre.OUVERTE).stream()
                .filter(v -> v.toLowerCase().startsWith("zz-filtre")).toList();
        List<String> technologies = offreRepository.technologiesDistinctes(StatutOffre.OUVERTE).stream()
                .filter(t -> t.toLowerCase().startsWith("zz")).toList();

        assertThat(villes).hasSize(2).allMatch(v -> v.equalsIgnoreCase(VILLE) || v.equalsIgnoreCase(AUTRE_VILLE));
        assertThat(technologies).hasSize(4); // zzJava/ZZJAVA comptés une fois ; zzSQL de l'offre ouverte
    }

    @Test
    void endpointPublic_appliqueLesFiltres() throws Exception {
        mockMvc.perform(get("/api/v1/offres")
                        .param("ville", "zz-filtre-rabat")
                        .param("technologies", "zzspring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(javaSpring.getId().intValue()));
    }

    // --- Données de test ---

    private List<Offre> rechercher(OffreFiltre filtre) {
        return offreRepository.findAll(OffreSpecifications.ouvertesCorrespondantA(filtre), PageRequest.of(0, 20))
                .getContent();
    }

    private Entreprise entreprise() {
        // L'id du profil est celui d'un compte d'auth-service (ici un id libre, sans compte)
        Entreprise entreprise = new Entreprise();
        entreprise.setId(ApiDeTest.nouvelId());
        entreprise.setEmail("zz-filtre@test.ma");
        entreprise.setNom("ZZ Filtres");
        entreprise.setSecteur("Test");
        entreprise.setVille(VILLE);
        entreprise.setStatutValidation(StatutValidation.VALIDEE);
        return entrepriseRepository.save(entreprise);
    }

    private Offre offre(Entreprise entreprise, String ville, String domaine, StatutOffre statut, String... technologies) {
        Offre offre = new Offre();
        offre.setEntreprise(entreprise);
        offre.setTitre("Offre " + domaine);
        offre.setDescription("Description");
        offre.setVille(ville);
        offre.setDomaine(domaine);
        offre.getTechnologies().addAll(List.of(technologies));
        offre.setDureeMois(6);
        offre.setDateLimite(LocalDate.now().plusDays(30));
        offre.setStatut(statut);
        return offreRepository.save(offre);
    }
}
