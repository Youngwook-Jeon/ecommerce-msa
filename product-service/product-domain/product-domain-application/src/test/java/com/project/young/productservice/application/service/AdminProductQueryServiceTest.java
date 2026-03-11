package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductQueryServiceTest {

    @Mock
    private AdminProductReadRepository adminProductReadRepository;

    @InjectMocks
    private AdminProductQueryService adminProductQueryService;

    @Test
    @DisplayName("search: 조건과 페이징 정보를 그대로 리포지토리에 위임하고 결과를 반환한다")
    void search_DelegatesToRepositoryAndReturnsResult() {
        // Given
        AdminProductSearchCondition condition = new AdminProductSearchCondition(
                1L,
                true,
                ProductStatus.ACTIVE,
                "브랜드A",
                "데님"
        );

        UUID productId = UUID.randomUUID();
        ReadProductView view = ReadProductView.builder()
                .id(productId)
                .categoryId(1L)
                .name("와이드핏 데님")
                .description("와이드핏 데님 상세 설명입니다.")
                .brand("브랜드A")
                .mainImageUrl("https://example.com/image.jpg")
                .basePrice(new BigDecimal("99000"))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .build();

        AdminProductReadRepository.AdminProductSearchResult repositoryResult =
                new AdminProductReadRepository.AdminProductSearchResult(
                        List.of(view),
                        0,
                        20,
                        1,
                        1
                );

        when(adminProductReadRepository.search(eq(condition), eq(0), eq(20), eq("createdAt"), eq(false)))
                .thenReturn(repositoryResult);

        // When
        AdminProductReadRepository.AdminProductSearchResult result =
                adminProductQueryService.search(condition, 0, 20, "createdAt", false);

        // Then
        assertThat(result).isSameAs(repositoryResult);
        verify(adminProductReadRepository).search(eq(condition), eq(0), eq(20), eq("createdAt"), eq(false));
    }
}

