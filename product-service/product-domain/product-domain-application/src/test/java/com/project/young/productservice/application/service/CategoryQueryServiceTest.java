package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceTest {

    @Mock
    private CategoryReadRepository categoryReadRepository;

    @InjectMocks
    private CategoryQueryService categoryQueryService;

    @Test
    @DisplayName("getAllActiveCategoryHierarchy: repository를 호출하고 결과를 그대로 반환한다")
    void getAllActiveCategoryHierarchy_ReturnsRepositoryResult() {
        // Given
        List<ReadCategoryView> mockHierarchy = List.of(
                new ReadCategoryView(1L, "Root", null, CategoryStatus.ACTIVE, List.of(
                        new ReadCategoryView(2L, "Child", 1L, CategoryStatus.ACTIVE, List.of())
                ))
        );

        when(categoryReadRepository.findAllActiveCategoryHierarchy()).thenReturn(mockHierarchy);

        // When
        List<ReadCategoryView> result = categoryQueryService.getAllActiveCategoryHierarchy();

        // Then
        assertThat(result).isSameAs(mockHierarchy);
        verify(categoryReadRepository, times(1)).findAllActiveCategoryHierarchy();
        verify(categoryReadRepository, never()).findAllCategoryHierarchy();
        verifyNoMoreInteractions(categoryReadRepository);
    }

    @Test
    @DisplayName("getAdminCategoryHierarchy: repository를 호출하고 결과를 그대로 반환한다")
    void getAdminCategoryHierarchy_ReturnsRepositoryResult() {
        // Given
        List<ReadCategoryView> mockHierarchy = List.of(
                new ReadCategoryView(1L, "Root", null, CategoryStatus.ACTIVE, List.of())
        );

        when(categoryReadRepository.findAllCategoryHierarchy()).thenReturn(mockHierarchy);

        // When
        List<ReadCategoryView> result = categoryQueryService.getAdminCategoryHierarchy();

        // Then
        assertThat(result).isSameAs(mockHierarchy);
        verify(categoryReadRepository, times(1)).findAllCategoryHierarchy();
        verify(categoryReadRepository, never()).findAllActiveCategoryHierarchy();
        verifyNoMoreInteractions(categoryReadRepository);
    }
}