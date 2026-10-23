package com.stagehub.entreprise.mapper;

import com.stagehub.common.config.MapStructConfig;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.entity.Entreprise;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface EntrepriseMapper {

    EntrepriseResponse toResponse(Entreprise entreprise);
}
