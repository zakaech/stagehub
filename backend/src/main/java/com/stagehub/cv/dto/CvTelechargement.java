package com.stagehub.cv.dto;

import org.springframework.core.io.Resource;

/** Fichier CV prêt à être renvoyé, avec le nom proposé au navigateur ("CV Sara Alaoui.pdf"). */
public record CvTelechargement(Resource contenu, String nomFichier) {
}
