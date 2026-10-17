package com.stagehub.offre.repository;

import com.stagehub.IntegrationTestBase;
import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fermeture en masse sur un vrai PostgreSQL ; transaction annulée à la fin du test. */
@Transactional
class FermetureOffresIT extends IntegrationTestBase {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2001, 3, 15);

    @Autowired
    private OffreRepository offreRepository;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    void fermeUniquementLesOffresOuvertesDontLaDateLimiteEstStrictementPassee() {
        Entreprise entreprise = entreprise();
        Offre hier = offre(entreprise, AUJOURDHUI.minusDays(1), StatutOffre.OUVERTE);
        Offre aujourdhui = offre(entreprise, AUJOURDHUI, StatutOffre.OUVERTE);
        Offre demain = offre(entreprise, AUJOURDHUI.plusDays(1), StatutOffre.OUVERTE);
        Offre dejaFermee = offre(entreprise, AUJOURDHUI.minusDays(10), StatutOffre.FERMEE);

        int fermees = offreRepository.fermerOffresExpirees(AUJOURDHUI, StatutOffre.OUVERTE, StatutOffre.FERMEE);

        // Date de référence en 2001 : les offres des autres tests (dates actuelles) ne sont pas concernées
        assertThat(fermees).isEqualTo(1);
        assertThat(statuts(hier, aujourdhui, demain, dejaFermee)).containsExactly(
                StatutOffre.FERMEE, StatutOffre.OUVERTE, StatutOffre.OUVERTE, StatutOffre.FERMEE);
    }

    /** Relit en base : le contexte de persistance a été vidé par la mise à jour en masse (clearAutomatically). */
    private List<StatutOffre> statuts(Offre... offres) {
        return List.of(offres).stream()
                .map(o -> offreRepository.findById(o.getId()).orElseThrow().getStatut())
                .toList();
    }

    private Entreprise entreprise() {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail("zz-fermeture@test.ma");
        utilisateur.setMotDePasse("hash-non-utilise");
        utilisateur.setRole(Role.ENTREPRISE);
        Entreprise entreprise = new Entreprise();
        entreprise.setUtilisateur(utilisateurRepository.save(utilisateur));
        entreprise.setNom("ZZ Fermeture");
        entreprise.setSecteur("Test");
        entreprise.setVille("Rabat");
        entreprise.setStatutValidation(StatutValidation.VALIDEE);
        return entrepriseRepository.save(entreprise);
    }

    private Offre offre(Entreprise entreprise, LocalDate dateLimite, StatutOffre statut) {
        Offre offre = new Offre();
        offre.setEntreprise(entreprise);
        offre.setTitre("Offre " + dateLimite);
        offre.setDescription("Description");
        offre.setVille("Rabat");
        offre.setDomaine("Dev");
        offre.getTechnologies().add("Java");
        offre.setDureeMois(6);
        offre.setDateLimite(dateLimite);
        offre.setStatut(statut);
        return offreRepository.save(offre);
    }
}
