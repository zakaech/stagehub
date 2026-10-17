package com.stagehub.audit.aspect;

import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * L'aspect est testé à travers un vrai proxy Spring AOP (AspectJProxyFactory), sans contexte Spring :
 * c'est exactement le mécanisme utilisé en production, y compris ses limites (auto-invocation).
 */
class AuditAspectTest {

    /** Service factice dont l'"entité" est un simple champ statut. */
    static class ServiceFictif {
        String statut = "EN_ATTENTE";

        @Audite(value = ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, idParam = "candidatureId")
        public void changer(Long candidatureId, String nouveauStatut) {
            statut = nouveauStatut;
        }

        @Audite(value = ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, idParam = "candidatureId")
        public void echouer(Long candidatureId) {
            throw new IllegalStateException("transition refusée");
        }

        @Audite(value = ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, idParam = "inexistant")
        public void mauvaisParametre(Long candidatureId) {
        }

        /** Appel interne : passe par "this", pas par le proxy. */
        public void changerEnInterne(Long candidatureId) {
            this.changer(candidatureId, "REFUSEE");
        }
    }

    private final AuditService auditService = mock(AuditService.class);
    private ServiceFictif cible;
    private ServiceFictif proxy;

    @BeforeEach
    void setUp() {
        cible = new ServiceFictif();
        LecteurEtatAudit lecteur = new LecteurEtatAudit() {
            @Override
            public EntiteAuditee entite() {
                return EntiteAuditee.CANDIDATURE;
            }

            @Override
            public Optional<String> lireEtat(Long id) {
                return Optional.of(cible.statut);
            }
        };
        AspectJProxyFactory fabrique = new AspectJProxyFactory(cible);
        fabrique.setProxyTargetClass(true);
        fabrique.addAspect(new AuditAspect(auditService, List.of(lecteur)));
        proxy = fabrique.getProxy();
    }

    @Test
    void appelViaLeProxy_enregistreLEtatAvantEtApres() {
        proxy.changer(7L, "ENTRETIEN");

        verify(auditService).enregistrer(ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, 7L, "EN_ATTENTE", "ENTRETIEN");
    }

    @Test
    void actionEnEchec_rienNestEnregistre() {
        assertThatThrownBy(() -> proxy.echouer(7L)).hasMessage("transition refusée");

        verifyNoInteractions(auditService);
    }

    @Test
    void autoInvocation_contourneLeProxy_doncPasDAudit() {
        proxy.changerEnInterne(7L);

        // L'action a bien eu lieu... mais l'appel interne this.changer() n'est pas passé par l'aspect
        assertThat(cible.statut).isEqualTo("REFUSEE");
        verifyNoInteractions(auditService);
    }

    @Test
    void parametreIdIntrouvable_erreurExplicite() {
        assertThatThrownBy(() -> proxy.mauvaisParametre(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inexistant");
    }
}
