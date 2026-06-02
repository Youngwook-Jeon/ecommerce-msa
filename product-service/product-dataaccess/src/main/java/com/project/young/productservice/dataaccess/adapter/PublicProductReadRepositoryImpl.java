package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.dataaccess.projection.PublicProductListProjection;
import com.project.young.productservice.dataaccess.repository.PublicProductSearchQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class PublicProductReadRepositoryImpl implements PublicProductReadRepository {

    private final PublicProductSearchQueryRepository publicProductSearchQueryRepository;
    private final ProductReadRepository productReadRepository;

    public PublicProductReadRepositoryImpl(PublicProductSearchQueryRepository publicProductSearchQueryRepository,
                                           ProductReadRepository productReadRepository) {
        this.publicProductSearchQueryRepository = publicProductSearchQueryRepository;
        this.productReadRepository = productReadRepository;
    }

    @Override
    public PublicProductListPageResult search(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<PublicProductListProjection> rowPage =
                publicProductSearchQueryRepository.search(condition, sort, pageable);

        List<ReadPublicProductSummaryView> content = rowPage.getContent().stream()
                .map(this::toReadPublicProductSummaryView)
                .toList();

        return new PublicProductListPageResult(
                content,
                rowPage.getNumber(),
                rowPage.getSize(),
                rowPage.getTotalElements(),
                rowPage.getTotalPages()
        );
    }

    private ReadPublicProductSummaryView toReadPublicProductSummaryView(PublicProductListProjection row) {
        return ReadPublicProductSummaryView.builder()
                .id(row.id())
                .categoryId(row.categoryId())
                .name(row.name())
                .brand(row.brand())
                .mainImageUrl(row.mainImageUrl())
                .basePrice(row.basePrice())
                .build();
    }

    @Override
    public Optional<ReadProductDetailView> findStorefrontProductDetailById(ProductId productId) {
        return productReadRepository.findStorefrontProductDetailById(productId);
    }
}
