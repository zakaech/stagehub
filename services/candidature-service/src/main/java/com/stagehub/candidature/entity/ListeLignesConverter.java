package com.stagehub.candidature.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Liste de textes courts stockée dans une colonne TEXT, une valeur par ligne (résumé du CV, technologies retrouvées).
 * Plus simple qu'une table à part ou qu'une colonne JSON pour cinq lignes au plus, jamais filtrées en SQL. Les
 * valeurs ne contiennent pas de saut de ligne (lignes du résumé validées par ai-service, technologies des offres).
 */
@Converter
public class ListeLignesConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> lignes) {
        return lignes == null || lignes.isEmpty() ? null : String.join("\n", lignes);
    }

    @Override
    public List<String> convertToEntityAttribute(String colonne) {
        return colonne == null || colonne.isEmpty() ? List.of() : List.of(colonne.split("\n"));
    }
}
