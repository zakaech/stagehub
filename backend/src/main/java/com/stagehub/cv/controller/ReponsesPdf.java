package com.stagehub.cv.controller;

import com.stagehub.cv.dto.CvTelechargement;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Construit la réponse HTTP d'un téléchargement de CV. */
final class ReponsesPdf {

    private ReponsesPdf() {
    }

    static ResponseEntity<Resource> telechargement(CvTelechargement cv) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // "attachment" : le navigateur enregistre le fichier au lieu de l'afficher ;
                // filename* encode les accents (RFC 6266)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(cv.nomFichier(), StandardCharsets.UTF_8).build().toString())
                // Document personnel : aucun cache (navigateur ou proxy) ne doit le conserver
                .cacheControl(CacheControl.noStore())
                .body(cv.contenu());
    }
}
