package com.stagehub.entreprise.service;

import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.common.exception.EntrepriseNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.event.EntrepriseValidationEvent;
import com.stagehub.entreprise.mapper.EntrepriseMapper;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntrepriseServiceTest {

    @Mock
    private EntrepriseRepository entrepriseRepository;
    @Mock
    private ApplicationEventPublisher evenements;

    private EntrepriseService service;

    @BeforeEach
    void setUp() {
        service = new EntrepriseService(entrepriseRepository, Mappers.getMapper(EntrepriseMapper.class), evenements);
    }

    @Test
    void lister_avecOuSansFiltreDeStatut() {
        PageRequest page = PageRequest.of(0, 10);
        Entreprise acme = entreprise(StatutValidation.EN_ATTENTE);
        when(entrepriseRepository.findAll(page)).thenReturn(new PageImpl<>(List.of(acme), page, 1));
        when(entrepriseRepository.findByStatutValidation(StatutValidation.VALIDEE, page))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        assertThat(service.lister(null, page).content()).extracting(EntrepriseResponse::nom).containsExactly("Acme");
        assertThat(service.lister(StatutValidation.VALIDEE, page).totalElements()).isZero();
    }

    @Test
    void statuer_valideLEntreprisePuisPublieLEvenement() {
        Entreprise acme = entreprise(StatutValidation.EN_ATTENTE);
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.of(acme));

        EntrepriseResponse reponse = service.statuer(2L, StatutValidation.VALIDEE);

        assertThat(reponse.statutValidation()).isEqualTo(StatutValidation.VALIDEE);
        verify(evenements).publishEvent(new EntrepriseValidationEvent(2L, "rh@acme.ma", "Acme", StatutValidation.VALIDEE));
    }

    @Test
    void statuer_entrepriseDejaValidee_leve409SansEvenement() {
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.of(entreprise(StatutValidation.VALIDEE)));

        assertThatThrownBy(() -> service.statuer(2L, StatutValidation.REFUSEE))
                .isInstanceOf(TransitionStatutInvalideException.class);
        verifyNoInteractions(evenements);
    }

    @Test
    void statuer_entrepriseInexistante_leve404() {
        when(entrepriseRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.statuer(9L, StatutValidation.VALIDEE))
                .isInstanceOf(EntrepriseNotFoundException.class);
    }

    private static Entreprise entreprise(StatutValidation statut) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail("rh@acme.ma");
        Entreprise entreprise = new Entreprise();
        entreprise.setId(2L);
        entreprise.setUtilisateur(utilisateur);
        entreprise.setNom("Acme");
        entreprise.setStatutValidation(statut);
        return entreprise;
    }
}
