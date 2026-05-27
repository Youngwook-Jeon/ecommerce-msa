package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.PublicProductFacetReadRepository;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductFacetQueryServiceTest {

    private static final long CATEGORY_ID = 12L;

    @Mock
    private CategoryReadRepository categoryReadRepository;

    @Mock
    private PublicProductFacetReadRepository publicProductFacetReadRepository;

    private PublicProductFacetQueryService service;

    @BeforeEach
    void setUp() {
        service = new PublicProductFacetQueryService(categoryReadRepository, publicProductFacetReadRepository);
    }

    @Test
    @DisplayName("정상 요청이면 정규화 후 repository 결과를 반환한다")
    void getFacets_withValidQuery_returnsRepositoryResult() {
        PublicProductFacetResult expected = new PublicProductFacetResult(CATEGORY_ID, 0L, List.of(), List.of());
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(true);
        when(publicProductFacetReadRepository.getFacets(any(PublicProductFacetQuery.class))).thenReturn(expected);

        PublicProductFacetResult result = service.getFacets(
                new PublicProductFacetQuery(
                        CATEGORY_ID,
                        " denim ",
                        List.of(" BrandA ", "BrandA", "", "BrandB"),
                        new BigDecimal("10"),
                        new BigDecimal("100"),
                        List.of()
                )
        );

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<PublicProductFacetQuery> queryCaptor = ArgumentCaptor.forClass(PublicProductFacetQuery.class);
        verify(publicProductFacetReadRepository).getFacets(queryCaptor.capture());
        PublicProductFacetQuery normalized = queryCaptor.getValue();
        assertThat(normalized.q()).isEqualTo("denim");
        assertThat(normalized.brands()).containsExactly("BrandA", "BrandB");
        assertThat(normalized.facets()).containsExactlyInAnyOrder(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE);
    }

    @Test
    @DisplayName("카테고리가 없거나 비활성이면 CategoryNotFoundException")
    void getFacets_whenCategoryMissing_throws() {
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getFacets(
                new PublicProductFacetQuery(CATEGORY_ID, null, List.of(), null, null, List.of())
        )).isInstanceOf(CategoryNotFoundException.class);

        verify(publicProductFacetReadRepository, never()).getFacets(any());
    }
}
