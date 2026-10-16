package com.stagehub.cv.service;

import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.common.storage.FichierStorage;
import com.stagehub.cv.config.CvProperties;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.mapper.EtudiantMapper;
import com.stagehub.etudiant.repository.EtudiantRepository;
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

    private final EtudiantRepository etudiantRepository;
    private final EtudiantMapper etudiantMapper;
    private final FichierStorage cvStorage;
    private final CvProperties properties;

    public CvService(EtudiantRepository etudiantRepository, EtudiantMapper etudiantMapper,
                     FichierStorage cvStorage, CvProperties properties) {
        this.etudiantRepository = etudiantRepository;
        this.etudiantMapper = etudiantMapper;
        this.cvStorage = cvStorage;
        this.properties = properties;
    }

    /**
     * Dépose (ou remplace) le CV de l'étudiant. Le fichier est écrit avant le commit de la base :
     * selon l'issue de la transaction, on supprime ensuite l'ancien fichier (succès) ou le nouveau (échec).
     */
    @Transactional
    public EtudiantResponse deposer(Long etudiantId, MultipartFile fichier) {
        PdfValidator.valider(fichier, properties.tailleMax());
        Etudiant etudiant = etudiantRepository.findAvecVerrou(etudiantId)
                .orElseThrow(() -> new UtilisateurNotFoundException(etudiantId));

        String nouveau = enregistrer(fichier);
        String ancien = etudiant.getCheminCv();
        etudiant.setCheminCv(nouveau);

        nettoyerApresTransaction(nouveau, ancien);
        return etudiantMapper.toResponse(etudiant);
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
