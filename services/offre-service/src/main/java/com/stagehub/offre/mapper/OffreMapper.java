package com.stagehub.offre.mapper;

import com.stagehub.common.config.MapStructConfig;
import com.stagehub.offre.dto.OffreRequest;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.entity.Offre;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface OffreMapper {

    @Mapping(target = "entrepriseId", source = "entreprise.id")
    @Mapping(target = "entrepriseNom", source = "entreprise.nom")
    OffreResponse toResponse(Offre offre);

    // id, statut et entreprise ne viennent jamais du client : le service les fixe lui-même
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "entreprise", ignore = true)
    Offre toEntity(OffreRequest request);

    /** Mise à jour en place : la collection technologies est vidée puis remplie (pas remplacée). */
    @InheritConfiguration(name = "toEntity")
    void mettreAJour(OffreRequest request, @MappingTarget Offre offre);

    /** Appelé par MapStruct à la fin de toEntity et de mettreAJour : création et modification sont nettoyées. */
    @AfterMapping
    default void normaliser(@MappingTarget Offre offre) {
        offre.normaliserChampsDeFiltre();
    }
}
