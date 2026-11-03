package com.stagehub.ai.cv;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lit le CV figé d'une candidature (candidature-service, API interne) et en extrait le texte.
 * Une panne de candidature-service ou d'auth-service se propage : l'analyse sera retentée plus tard.
 * Un CV absent ou illisible, lui, ne changera pas : exceptions distinctes ({@link CvAbsentException},
 * {@link CvIllisibleException}).
 */
@Service
public class LectureCv {

    private static final Logger log = LoggerFactory.getLogger(LectureCv.class);

    private final CandidatureClient candidatureClient;
    private final ExtractionTexteCv extraction;

    public LectureCv(CandidatureClient candidatureClient, ExtractionTexteCv extraction) {
        this.candidatureClient = candidatureClient;
        this.extraction = extraction;
    }

    public TexteCv texteDuCv(Long candidatureId) {
        byte[] pdf;
        try {
            pdf = candidatureClient.cv(candidatureId);
        } catch (FeignException.NotFound e) {
            throw new CvAbsentException(candidatureId);
        }
        TexteCv texte = extraction.extraire(pdf);
        // Ni le texte ni le nom de fichier : une donnée personnelle n'a rien à faire dans les logs
        log.info("CV de la candidature {} lu : {} page(s), {} caractères{}", candidatureId, texte.pages(),
                texte.texte().length(), texte.tronque() ? " (tronqué)" : "");
        return texte;
    }
}
