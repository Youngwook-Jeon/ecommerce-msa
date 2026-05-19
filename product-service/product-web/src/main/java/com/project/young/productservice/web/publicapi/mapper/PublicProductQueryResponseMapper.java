package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.web.publicapi.dto.PublicProductPageResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicProductQueryResponseMapper {

    public PublicProductPageResponse toPublicProductPageResponse(PublicProductListPageResult result) {
        List<PublicProductSummaryResponse> content = result.content().stream()
                .map(this::toSummary)
                .toList();

        return PublicProductPageResponse.builder()
                .content(content)
                .page(result.page())
                .size(result.size())
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .build();
    }

    private PublicProductSummaryResponse toSummary(ReadPublicProductSummaryView view) {
        return PublicProductSummaryResponse.builder()
                .id(view.id())
                .name(view.name())
                .brand(view.brand())
                .mainImageUrl(view.mainImageUrl())
                .basePrice(view.basePrice())
                .build();
    }
}
