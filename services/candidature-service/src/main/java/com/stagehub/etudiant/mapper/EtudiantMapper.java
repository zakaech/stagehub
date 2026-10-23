package com.stagehub.etudiant.mapper;

import com.stagehub.common.config.MapStructConfig;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.etudiant.entity.Etudiant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface EtudiantMapper {

    @Mapping(target = "cvDisponible", expression = "java(etudiant.getCheminCv() != null)")
    EtudiantResponse toResponse(Etudiant etudiant);
}
