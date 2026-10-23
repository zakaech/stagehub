package com.stagehub.cv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/** {@code stagehub.cv.*} : dossier de stockage ({@code CV_STORAGE_DIR}) et taille maximale d'un CV. */
@ConfigurationProperties("stagehub.cv")
public record CvProperties(Path dossier, DataSize tailleMax) {

    public CvProperties {
        if (dossier == null) {
            dossier = Path.of("uploads", "cv");
        }
        if (tailleMax == null) {
            tailleMax = DataSize.ofMegabytes(5);
        }
    }
}
