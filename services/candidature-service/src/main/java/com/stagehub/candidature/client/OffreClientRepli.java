package com.stagehub.candidature.client;

import com.stagehub.candidature.dto.OffreResumeResponse;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.ServiceIndisponibleException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Réponse de repli quand un appel à offre-service échoue (erreur, délai dépassé, circuit ouvert) :
 * <ul>
 *   <li>offre inconnue (404 d'offre-service) : ce n'est pas une panne, 404 normal ;</li>
 *   <li>lire UNE offre (pour postuler) : 503, on refuse plutôt que d'accepter une candidature non vérifiée ;</li>
 *   <li>résumés d'une liste : liste vide, la liste s'affiche avec les données recopiées (mode dégradé).</li>
 * </ul>
 */
@Component
public class OffreClientRepli implements FallbackFactory<OffreClient> {

    private static final Logger log = LoggerFactory.getLogger(OffreClientRepli.class);
    static final String MESSAGE = "Le service des offres est momentanément indisponible, réessayez dans un instant";

    @Override
    public OffreClient create(Throwable cause) {
        return new OffreClient() {
            @Override
            public OffreInterne offre(Long id) {
                if (cause instanceof FeignException.NotFound) {
                    throw new OffreNotFoundException(id);
                }
                log.warn("Offre {} illisible ({}) : candidature refusée (503)", id, cause.toString());
                throw new ServiceIndisponibleException(MESSAGE);
            }

            @Override
            public List<OffreResumeResponse> resumes(Collection<Long> ids) {
                log.warn("Résumés d'offres illisibles ({}) : affichage des données recopiées", cause.toString());
                return List.of();
            }
        };
    }
}
