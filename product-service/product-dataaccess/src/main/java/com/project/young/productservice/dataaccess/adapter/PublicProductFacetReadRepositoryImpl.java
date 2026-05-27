package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.port.output.PublicProductFacetReadRepository;
import com.project.young.productservice.dataaccess.repository.PublicProductFacetQueryRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PublicProductFacetReadRepositoryImpl implements PublicProductFacetReadRepository {

    private final PublicProductFacetQueryRepository publicProductFacetQueryRepository;

    public PublicProductFacetReadRepositoryImpl(PublicProductFacetQueryRepository publicProductFacetQueryRepository) {
        this.publicProductFacetQueryRepository = publicProductFacetQueryRepository;
    }

    @Override
    public PublicProductFacetResult getFacets(PublicProductFacetQuery query) {
        return publicProductFacetQueryRepository.getFacets(query);
    }
}
