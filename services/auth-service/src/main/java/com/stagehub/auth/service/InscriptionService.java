package com.stagehub.auth.service;

import com.stagehub.auth.client.CreationProfilEntreprise;
import com.stagehub.auth.client.CreationProfilEtudiant;
import com.stagehub.auth.client.ProfilEntrepriseClient;
import com.stagehub.auth.client.ProfilEtudiantClient;
import com.stagehub.auth.dto.ProfilEntreprise;
import com.stagehub.auth.dto.ProfilEtudiant;
import com.stagehub.auth.dto.ProfilResponse;
import com.stagehub.auth.dto.RegisterEntrepriseRequest;
import com.stagehub.auth.dto.RegisterEtudiantRequest;
import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.exception.ServiceIndisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Inscription = <b>saga orchestrée</b>. Le compte (base d'auth-service) et le profil (base d'un autre service) ne
 * peuvent pas être créés dans une même transaction : chaque étape a sa transaction locale, et si une étape échoue, on
 * annule les précédentes par une <b>action de compensation</b> (ici : supprimer le compte).
 * <ol>
 *   <li>créer le compte EN_ATTENTE (transaction locale) — il ne peut pas encore se connecter ;</li>
 *   <li>créer le profil dans l'autre service (appel Feign) — en cas d'échec : compensation (profil éventuellement
 *       créé, puis compte), 503 ;</li>
 *   <li>activer le compte (transaction locale).</li>
 * </ol>
 * Si auth-service s'arrête entre 2 et 3, le compte reste EN_ATTENTE (donc inutilisable) et
 * {@link NettoyageInscriptionsJob} termine la compensation plus tard (profil puis compte supprimés).
 * Volontairement <b>pas de @Transactional ici</b> : on ne garde pas une connexion et des verrous en base pendant un
 * appel réseau de plusieurs secondes.
 */
@Service
public class InscriptionService {

    private static final Logger log = LoggerFactory.getLogger(InscriptionService.class);
    static final String MESSAGE_INDISPONIBLE = "Inscription momentanément impossible, réessayez dans un instant";

    private final CompteService compteService;
    private final ProfilEtudiantClient profilEtudiantClient;
    private final ProfilEntrepriseClient profilEntrepriseClient;

    public InscriptionService(CompteService compteService, ProfilEtudiantClient profilEtudiantClient,
                              ProfilEntrepriseClient profilEntrepriseClient) {
        this.compteService = compteService;
        this.profilEtudiantClient = profilEtudiantClient;
        this.profilEntrepriseClient = profilEntrepriseClient;
    }

    public ProfilResponse inscrireEtudiant(RegisterEtudiantRequest r) {
        Utilisateur compte = compteService.creerEnAttente(r.email(), r.motDePasse(), Role.ETUDIANT);
        creerProfilOuCompenser(compte,
                () -> profilEtudiantClient.creer(new CreationProfilEtudiant(
                        compte.getId(), compte.getEmail(), r.nom(), r.prenom(), r.ecole(), r.filiere(), r.ville())),
                () -> profilEtudiantClient.supprimer(compte.getId()));
        Utilisateur actif = compteService.activer(compte.getId());
        ProfilEtudiant profil = new ProfilEtudiant(actif.getId(), r.nom(), r.prenom(), r.ecole(), r.filiere(), r.ville(), false);
        return new ProfilResponse(actif.getId(), actif.getEmail(), actif.getRole(), actif.getDateCreation(), profil, null);
    }

    /** Le profil entreprise est créé EN_ATTENTE de validation par l'admin (règle métier du service des profils). */
    public ProfilResponse inscrireEntreprise(RegisterEntrepriseRequest r) {
        Utilisateur compte = compteService.creerEnAttente(r.email(), r.motDePasse(), Role.ENTREPRISE);
        creerProfilOuCompenser(compte,
                () -> profilEntrepriseClient.creer(new CreationProfilEntreprise(
                        compte.getId(), compte.getEmail(), r.nom(), r.secteur(), r.ville(), r.description())),
                () -> profilEntrepriseClient.supprimer(compte.getId()));
        Utilisateur actif = compteService.activer(compte.getId());
        ProfilEntreprise profil = new ProfilEntreprise(actif.getId(), actif.getEmail(), r.nom(), r.secteur(), r.ville(),
                r.description(), "EN_ATTENTE");
        return new ProfilResponse(actif.getId(), actif.getEmail(), actif.getRole(), actif.getDateCreation(), null, profil);
    }

    private void creerProfilOuCompenser(Utilisateur compte, Runnable creationProfil, Runnable suppressionProfil) {
        try {
            creationProfil.run();
        } catch (RuntimeException e) {
            // Service des profils injoignable, trop lent ou en erreur : on défait ce qui a pu être fait
            log.warn("Création du profil {} impossible ({}) : compensation", compte.getId(), e.getMessage());
            compenser(compte, suppressionProfil);
            throw new ServiceIndisponibleException(MESSAGE_INDISPONIBLE);
        }
    }

    /**
     * Un délai dépassé ne prouve pas que le profil n'a pas été créé (il a pu l'être juste après) : on tente aussi de le
     * supprimer. Si cette suppression échoue, le compte reste EN_ATTENTE et le nettoyage réessaiera plus tard ;
     * sinon le compte est supprimé tout de suite.
     */
    private void compenser(Utilisateur compte, Runnable suppressionProfil) {
        try {
            suppressionProfil.run();
        } catch (RuntimeException e) {
            log.warn("Compensation : suppression du profil {} impossible ({}), confiée au nettoyage",
                    compte.getId(), e.getMessage());
            return;
        }
        compteService.supprimer(compte.getId());
    }
}
