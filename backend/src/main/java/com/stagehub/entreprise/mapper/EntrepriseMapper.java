package com.stagehub.entreprise.mapper;

import com.stagehub.common.config.MapStructConfig;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.entity.Entreprise;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface EntrepriseMapper {

    @Mapping(target = "email", source = "utilisateur.email")
    EntrepriseResponse toResponse(Entreprise entreprise);
}
