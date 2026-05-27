package com.project.young.productservice.web.converter;

import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts request param facet values (e.g. {@code brand}, {@code price}) to enum values.
 */
@Component
public class PublicProductFacetTypeRequestConverter implements Converter<String, PublicProductFacetType> {

    @Override
    public PublicProductFacetType convert(String source) {
        return PublicProductFacetType.fromApiValue(source);
    }
}
