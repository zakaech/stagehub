package com.stagehub.cv.service;

import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.CvNotFoundException;
import com.stagehub.common.exception.FichierNonPdfException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.common.storage.LocalFichierStorage;
import com.stagehub.cv.config.CvProperties;
import com.stagehub.cv.dto.CvTelechargement;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.mapper.EtudiantMapper;
import com.stagehub.etudiant.repository.EtudiantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Service testé avec un vrai stockage sur disque (dossier temporaire) et des repositories simulés.
 * La transaction est simulée : on initialise la synchronisation Spring, puis on déclenche à la main
 * "commit" ou "rollback" pour vérifier ce que le service fait des fichiers dans chaque cas.
 */
@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    private static final Long ETUDIANT_ID = 1L;

    @TempDir
    Path dossier;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private CandidatureRepository candidatureRepository;

    private CvService service;
    private LocalFichierStorage storage;
    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        storage = new LocalFichierStorage(dossier);
        service = new CvService(etudiantRepository, candidatureRepository, Mappers.getMapper(EtudiantMapper.class),
                storage, new CvProperties(dossier, DataSize.ofMegabytes(5)));
        etudiant = new Etudiant();
        etudiant.setId(ETUDIANT_ID);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deposer_stockeSousUnNomUuidSansJamaisUtiliserLeNomEnvoye() throws Exception {
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        var reponse = service.deposer(ETUDIANT_ID, pdf("../../evil.pdf"));
        terminerTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(reponse.cvDisponible()).isTrue();
        assertThat(etudiant.getCheminCv()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(Files.exists(dossier.resolve(etudiant.getCheminCv()))).isTrue();
        assertThat(Files.exists(dossier.resolveSibling("evil.pdf"))).isFalse();
    }

    @Test
    void remplacer_supprimeLAncienFichierApresLeCommit() {
        String ancien = storage.enregistrer(new ByteArrayInputStream(PdfValidatorTest.PDF_MINIMAL), ".pdf");
        etudiant.setCheminCv(ancien);
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        service.deposer(ETUDIANT_ID, pdf("nouveau.pdf"));
        assertThat(storage.charger(ancien)).as("rien n'est supprimé avant le commit").isPresent();
        terminerTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.charger(ancien)).isEmpty();
        assertThat(storage.charger(etudiant.getCheminCv())).isPresent();
    }

    @Test
    void remplacer_conserveLAncienFichierSIlEstFigeDansUneCandidature() {
        String ancien = storage.enregistrer(new ByteArrayInputStream(PdfValidatorTest.PDF_MINIMAL), ".pdf");
        etudiant.setCheminCv(ancien);
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
        when(candidatureRepository.existsByCvFichier(ancien)).thenReturn(true);

        service.deposer(ETUDIANT_ID, pdf("nouveau.pdf"));
        terminerTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.charger(ancien)).as("l'entreprise doit pouvoir télécharger le CV joint").isPresent();
        assertThat(storage.charger(etudiant.getCheminCv())).isPresent();
    }

    @Test
    void rollback_supprimeLeNouveauFichierEtGardeLAncien() {
        String ancien = storage.enregistrer(new ByteArrayInputStream(PdfValidatorTest.PDF_MINIMAL), ".pdf");
        etudiant.setCheminCv(ancien);
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        service.deposer(ETUDIANT_ID, pdf("nouveau.pdf"));
        String nouveau = etudiant.getCheminCv();
        terminerTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.charger(nouveau)).isEmpty();
        assertThat(storage.charger(ancien)).isPresent();
    }

    @Test
    void fichierNonPdf_estRefuseAvantToutEnregistrement() throws Exception {
        MockMultipartFile fauxPdf = new MockMultipartFile("fichier", "cv.pdf", "application/pdf", "pas un pdf".getBytes());

        assertThatThrownBy(() -> service.deposer(ETUDIANT_ID, fauxPdf)).isInstanceOf(FichierNonPdfException.class);
        try (var fichiers = Files.list(dossier)) {
            assertThat(fichiers).isEmpty();
        }
    }

    @Test
    void telechargerMonCv_sansCv_leveCvNotFound() {
        when(etudiantRepository.findById(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        assertThatThrownBy(() -> service.telechargerMonCv(ETUDIANT_ID)).isInstanceOf(CvNotFoundException.class);
    }

    @Test
    void telechargerCvCandidature_renvoieLeCvFigeAvecUnNomLisible() {
        String fige = storage.enregistrer(new ByteArrayInputStream(PdfValidatorTest.PDF_MINIMAL), ".pdf");
        etudiant.setCheminCv("un-autre-cv-plus-recent.pdf");
        when(candidatureRepository.findById(100L)).thenReturn(Optional.of(candidature(2L, fige)));

        CvTelechargement cv = service.telechargerCvCandidature(2L, 100L);

        assertThat(cv.contenu().getFilename()).isEqualTo(fige);
        assertThat(cv.nomFichier()).isEqualTo("CV Sara Alaoui.pdf");
    }

    @Test
    void telechargerCvCandidature_dUneAutreEntreprise_leveAccesNonAutorise() {
        when(candidatureRepository.findById(100L)).thenReturn(Optional.of(candidature(2L, "cv.pdf")));

        assertThatThrownBy(() -> service.telechargerCvCandidature(3L, 100L))
                .isInstanceOf(AccesNonAutoriseException.class);
    }

    @Test
    void telechargerCvCandidature_sansCvJoint_leveCvNotFound() {
        when(candidatureRepository.findById(100L)).thenReturn(Optional.of(candidature(2L, null)));

        assertThatThrownBy(() -> service.telechargerCvCandidature(2L, 100L)).isInstanceOf(CvNotFoundException.class);
    }

    private Candidature candidature(Long entrepriseId, String cvFichier) {
        etudiant.setPrenom("Sara");
        etudiant.setNom("Alaoui");
        Candidature candidature = new Candidature();
        candidature.setEntrepriseId(entrepriseId);
        candidature.setEtudiant(etudiant);
        candidature.setCvFichier(cvFichier);
        return candidature;
    }

    @Test
    void deposer_etudiantInexistant_leve404() {
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposer(ETUDIANT_ID, pdf("cv.pdf"))).isInstanceOf(UtilisateurNotFoundException.class);
    }

    @Test
    void deposer_lectureImpossibleDuFichier_erreurExplicite() throws Exception {
        MultipartFile fichier = mock(MultipartFile.class);
        when(fichier.isEmpty()).thenReturn(false);
        when(fichier.getSize()).thenReturn(100L);
        // 1re lecture (validation) : un vrai début de PDF ; 2e lecture (enregistrement) : erreur disque
        when(fichier.getInputStream())
                .thenReturn(new ByteArrayInputStream(PdfValidatorTest.PDF_MINIMAL))
                .thenThrow(new IOException("disque plein"));
        when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        assertThatThrownBy(() -> service.deposer(ETUDIANT_ID, fichier)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void telechargerMonCv_etudiantInexistant_leve404() {
        when(etudiantRepository.findById(ETUDIANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.telechargerMonCv(ETUDIANT_ID)).isInstanceOf(UtilisateurNotFoundException.class);
    }

    @Test
    void telechargerCvCandidature_inexistante_leve404() {
        when(candidatureRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.telechargerCvCandidature(2L, 100L))
                .isInstanceOf(CandidatureNotFoundException.class);
    }

    @Test
    void cvReferenceEnBaseMaisAbsentDuDisque_leve404() {
        etudiant.setCheminCv("00000000-0000-0000-0000-000000000000.pdf");
        when(etudiantRepository.findById(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));

        assertThatThrownBy(() -> service.telechargerMonCv(ETUDIANT_ID)).isInstanceOf(CvNotFoundException.class);
    }

    private static MockMultipartFile pdf(String nomEnvoye) {
        return new MockMultipartFile("fichier", nomEnvoye, "application/pdf", PdfValidatorTest.PDF_MINIMAL);
    }

    /** Simule la fin de transaction que Spring déclencherait après la méthode @Transactional. */
    private static void terminerTransaction(int statut) {
        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), statut);
    }
}
