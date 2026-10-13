package com.stagehub.common.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Configuration partagée par tous les mappers : beans Spring injectables, et compilation en échec
 * si un champ cible n'est pas mappé (on ne perd pas de donnée en silence).
 */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapStructConfig {
}
