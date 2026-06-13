package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductListQuery;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductQueryServiceTest {

    private static final long CATEGORY_ID = 12L;

    @Mock
    private CategoryReadRepository categoryReadRepository;

    @Mock
    private PublicProductReadRepository publicProductReadRepository;

    @Mock
    private StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    private PublicProductQueryService publicProductQueryService;

    @BeforeEach
    void setUp() {
        publicProductQueryService = new PublicProductQueryService(
                categoryReadRepository,
                publicProductReadRepository,
                storefrontProductDetailCachePort
        );
    }

    private void stubCachePassthrough() {
        when(storefrontProductDetailCachePort.getOrLoad(any(), any())).thenAnswer(invocation -> {
            Supplier<Optional<ReadProductDetailView>> loader = invocation.getArgument(1);
            return loader.get();
        });
    }

    @Test
    @DisplayName("ACTIVE 카테고리이면 repository 결과를 반환한다")
    void listProductsByCategory_whenCategoryActive_returnsPage() {
        PublicProductListPageResult expected = new PublicProductListPageResult(
                List.of(sampleView()),
                0,
                24,
                1L,
                1
        );
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(true);
        when(publicProductReadRepository.search(any(), eq(PublicProductSort.NEWEST), eq(0), eq(24)))
                .thenReturn(expected);

        PublicProductListPageResult result = publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, 0, 24, null, null, null, null, null)
        );

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("검색·정렬·퍼싯 파라미터를 condition과 sort로 전달한다")
    void listProductsByCategory_passesFiltersToRepository() {
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(true);
        when(publicProductReadRepository.search(any(), any(), eq(0), eq(24)))
                .thenReturn(new PublicProductListPageResult(List.of(), 0, 24, 0L, 0));

        publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(
                        CATEGORY_ID,
                        0,
                        24,
                        " denim ",
                        "price_asc",
                        List.of(" BrandA "),
                        new BigDecimal("10000"),
                        new BigDecimal("50000")
                )
        );

        ArgumentCaptor<PublicProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(PublicProductSearchCondition.class);
        verify(publicProductReadRepository).search(
                conditionCaptor.capture(),
                eq(PublicProductSort.PRICE_ASC),
                eq(0),
                eq(24)
        );

        PublicProductSearchCondition condition = conditionCaptor.getValue();
        assertThat(condition.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(condition.normalizedKeyword()).isEqualTo("denim");
        assertThat(condition.normalizedBrands()).containsExactly("BrandA");
        assertThat(condition.minPrice()).isEqualByComparingTo("10000");
        assertThat(condition.maxPrice()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("size가 상한을 넘으면 MAX_SIZE로 clamp하여 repository를 호출한다")
    void listProductsByCategory_whenSizeAboveMax_clampsToMaxSize() {
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(true);
        when(publicProductReadRepository.search(any(), any(), eq(0), eq(PublicProductQueryService.MAX_SIZE)))
                .thenReturn(new PublicProductListPageResult(List.of(), 0, PublicProductQueryService.MAX_SIZE, 0L, 0));

        publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, 0, 100, null, null, null, null, null)
        );

        verify(publicProductReadRepository).search(any(), any(), eq(0), eq(PublicProductQueryService.MAX_SIZE));
    }

    @Test
    @DisplayName("카테고리가 없거나 비활성이면 CategoryNotFoundException")
    void listProductsByCategory_whenCategoryMissing_throwsNotFound() {
        when(categoryReadRepository.existsActiveById(CATEGORY_ID)).thenReturn(false);

        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, 0, 24, null, null, null, null, null)
        ))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining(String.valueOf(CATEGORY_ID));

        verify(publicProductReadRepository, never()).search(any(), any(), eq(0), eq(24));
    }

    @Test
    @DisplayName("categoryId가 0 이하면 IllegalArgumentException")
    void listProductsByCategory_whenInvalidCategoryId_throws() {
        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(0, 0, 24, null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categoryId");

        verify(categoryReadRepository, never()).existsActiveById(anyLong());
    }

    @Test
    @DisplayName("page가 음수이면 IllegalArgumentException")
    void listProductsByCategory_whenNegativePage_throws() {
        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, -1, 24, null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    @DisplayName("size가 1 미만이면 IllegalArgumentException")
    void listProductsByCategory_whenSizeBelowOne_throws() {
        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, 0, 0, null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("잘못된 sort이면 IllegalArgumentException")
    void listProductsByCategory_whenInvalidSort_throws() {
        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(CATEGORY_ID, 0, 24, null, "invalid", null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort");
    }

    @Test
    @DisplayName("minPrice > maxPrice이면 IllegalArgumentException")
    void listProductsByCategory_whenInvalidPriceRange_throws() {
        assertThatThrownBy(() -> publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(
                        CATEGORY_ID, 0, 24, null, null, null, new BigDecimal("100"), new BigDecimal("50")
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    private static ReadPublicProductSummaryView sampleView() {
        return ReadPublicProductSummaryView.builder()
                .id(UUID.randomUUID())
                .categoryId(CATEGORY_ID)
                .name("Sample")
                .brand("Brand")
                .mainImageUrl("https://example.com/img.jpg")
                .basePrice(new BigDecimal("10000"))
                .build();
    }

    @Test
    @DisplayName("상품 상세 조회 - 조회 가능 상태면 상세를 반환한다")
    void getStorefrontProductDetail_whenExists_returnsDetail() {
        stubCachePassthrough();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView detail = ReadProductDetailView.builder()
                .id(productId.getValue())
                .categoryId(CATEGORY_ID)
                .name("Preview Product")
                .description("desc")
                .brand("Brand")
                .mainImageUrl("https://example.com/a.jpg")
                .basePrice(new BigDecimal("10000"))
                .status(ProductStatus.INACTIVE)
                .conditionType(ConditionType.NEW)
                .optionGroups(List.of())
                .variants(List.of())
                .build();

        when(publicProductReadRepository.findStorefrontProductDetailById(productId))
                .thenReturn(java.util.Optional.of(detail));

        ReadProductDetailView result = publicProductQueryService.getStorefrontProductDetail(productId);

        assertThat(result).isEqualTo(detail);
    }

    @Test
    @DisplayName("상품 상세 조회 - DRAFT/DELETED/없음은 ProductNotFoundException")
    void getStorefrontProductDetail_whenMissing_throwsNotFound() {
        stubCachePassthrough();
        ProductId productId = new ProductId(UUID.randomUUID());
        when(publicProductReadRepository.findStorefrontProductDetailById(productId))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> publicProductQueryService.getStorefrontProductDetail(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("not visible");
    }

    @Test
    @DisplayName("상품 상세 조회 - productId가 null이면 IllegalArgumentException")
    void getStorefrontProductDetail_whenNull_throwsIllegalArgument() {
        assertThatThrownBy(() -> publicProductQueryService.getStorefrontProductDetail(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
        verify(publicProductReadRepository, never()).findStorefrontProductDetailById(any());
    }
}
