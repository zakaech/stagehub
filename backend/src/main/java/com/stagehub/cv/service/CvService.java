package com.stagehub.cv.service;

import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.CvNotFoundException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.common.storage.FichierStorage;
import com.stagehub.cv.config.CvProperties;
import com.stagehub.cv.dto.CvTelechargement;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.mapper.EtudiantMapper;
import com.stagehub.etudiant.repository.EtudiantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Service
@Transactional(readOnly = true)
public class CvService {

    private static final Logger log = LoggerFactory.getLogger(CvService.class);

    private final EtudiantRepository etudiantRepository;
    private final CandidatureRepository candidatureRepository;
    private final EtudiantMapper etudiantMapper;
    private final FichierStorage cvStorage;
    private final CvProperties properties;

    public CvService(EtudiantRepository etudiantRepository, CandidatureRepository candidatureRepository,
                     EtudiantMapper etudiantMapper, FichierStorage cvStorage, CvProperties properties) {
        this.etudiantRepository = etudiantRepository;
        this.candidatureRepository = candidatureRepository;
        this.etudiantMapper = etudiantMapper;
        this.cvStorage = cvStorage;
        this.properties = properties;
    }

    /**
     * Dépose (ou remplace) le CV de l'étudiant. Le fichier est écrit avant le commit de la base :
     * selon l'issue de la transaction, on supprime ensuite l'ancien fichier (succès) ou le nouveau (échec).
     * L'ancien fichier est conservé s'il est figé dans une candidature.
     */
    @Transactional
    public EtudiantResponse deposer(Long etudiantId, MultipartFile fichier) {
        PdfValidator.valider(fichier, properties.tailleMax());
        // Verrou : une candidature simultanée du même étudiant attend, puis fige le nouveau CV
        Etudiant etudiant = etudiantRepository.findAvecVerrou(etudiantId)
                .orElseThrow(() -> new UtilisateurNotFoundException(etudiantId));

        String nouveau = enregistrer(fichier);
        String ancien = etudiant.getCheminCv();
        etudiant.setCheminCv(nouveau);

        // Vérifié maintenant, dans la transaction : après le commit, il n'y a plus de transaction pour interroger la base
        boolean ancienEncoreUtilise = ancien != null && candidatureRepository.existsByCvFichier(ancien);
        nettoyerApresTransaction(nouveau, ancienEncoreUtilise ? null : ancien);
        return etudiantMapper.toResponse(etudiant);
    }

    /** L'étudiant ne peut télécharger que son propre CV (l'id vient du JWT, jamais de l'URL). */
    public CvTelechargement telechargerMonCv(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new UtilisateurNotFoundException(etudiantId));
        return charger(etudiant.getCheminCv(), etudiant);
    }

    /**
     * Une entreprise ne télécharge que le CV joint à une candidature reçue sur l'une de ses offres :
     * passer par l'id de candidature encode la règle « l'étudiant a postulé chez moi ».
     */
    public CvTelechargement telechargerCvCandidature(Long entrepriseId, Long candidatureId) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new CandidatureNotFoundException(candidatureId));
        if (!candidature.getOffre().appartientA(entrepriseId)) {
            throw new AccesNonAutoriseException("Cette candidature ne concerne pas une offre de votre entreprise");
        }
        return charger(candidature.getCvFichier(), candidature.getEtudiant());
    }

    private CvTelechargement charger(String nomFichier, Etudiant etudiant) {
        if (nomFichier == null) {
            throw new CvNotFoundException();
        }
        return cvStorage.charger(nomFichier)
                .map(contenu -> new CvTelechargement(contenu,
                        "CV %s %s.pdf".formatted(etudiant.getPrenom(), etudiant.getNom())))
                .orElseThrow(() -> {
                    log.error("Fichier CV {} référencé en base mais absent du stockage", nomFichier);
                    return new CvNotFoundException();
                });
    }

    private String enregistrer(MultipartFile fichier) {
        try (InputStream contenu = fichier.getInputStream()) {
            // Nom généré par le stockage : le nom d'origine (fichier.getOriginalFilename()) n'est jamais utilisé
            return cvStorage.enregistrer(contenu, ".pdf");
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible", e);
        }
    }

    /** Enregistre une action exécutée par Spring une fois la transaction terminée (commit ou rollback). */
    private void nettoyerApresTransaction(String nouveau, String ancienASupprimer) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int statut) {
                if (statut == STATUS_COMMITTED) {
                    if (ancienASupprimer != null) {
                        cvStorage.supprimer(ancienASupprimer);
                    }
                } else {
                    // La base n'a pas été modifiée : le nouveau fichier ne serait référencé par personne
                    cvStorage.supprimer(nouveau);
                }
            }
        });
    }
}
