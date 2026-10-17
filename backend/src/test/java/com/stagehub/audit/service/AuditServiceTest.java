package com.stagehub.audit.service;

import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.audit.entity.AuditLog;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.audit.mapper.AuditLogMapper;
import com.stagehub.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-17T10:15:30Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogRepository, Mappers.getMapper(AuditLogMapper.class),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enregistrer_avecLUtilisateurAuthentifie() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin@stagehub.ma", null, List.of()));

        service.enregistrer(ActionAudit.VALIDATION_ENTREPRISE, 5L, "EN_ATTENTE", "VALIDEE");

        AuditLog log = enregistre();
        assertThat(log.getUtilisateur()).isEqualTo("admin@stagehub.ma");
        assertThat(log.getEntite()).isEqualTo(EntiteAuditee.ENTREPRISE);
        assertThat(log.getEntiteId()).isEqualTo(5L);
        assertThat(log.getDate()).isEqualTo(MAINTENANT);
    }

    @Test
    void enregistrer_horsRequete_utilisateurSysteme() {
        service.enregistrer(ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, 7L, "EN_ATTENTE", "ENTRETIEN");

        assertThat(enregistre().getUtilisateur()).isEqualTo(AuditService.SYSTEME);
    }

    @Test
    void lister_avecOuSansFiltre() {
        PageRequest page = PageRequest.of(0, 25);
        AuditLog log = new AuditLog();
        log.setAction(ActionAudit.VALIDATION_ENTREPRISE);
        when(auditLogRepository.findAll(page)).thenReturn(new PageImpl<>(List.of(log), page, 1));
        when(auditLogRepository.findByEntite(EntiteAuditee.CANDIDATURE, page)).thenReturn(new PageImpl<>(List.of(), page, 0));

        assertThat(service.lister(null, page).totalElements()).isEqualTo(1);
        assertThat(service.lister(EntiteAuditee.CANDIDATURE, page).content()).isEmpty();
    }

    private AuditLog enregistre() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
