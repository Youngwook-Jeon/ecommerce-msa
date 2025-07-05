package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.CategoryDto;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceTest {

    @Mock
    private CategoryReadRepository categoryReadRepository;

    @InjectMocks
    private CategoryQueryService categoryQueryService;

    @Test
    @DisplayName("Should call repository and return its result directly")
    void testGetCategoryHierarchy() {
        // Arrange
        // Create a mock DTO list. The test doesn't care how this list is constructed.
        List<CategoryDto> mockHierarchy = List.of(new CategoryDto(1L, "Test", null, List.of()));

        // Mock the repository port to return the predefined DTO list
        when(categoryReadRepository.findAllActiveCategoryHierarchy()).thenReturn(mockHierarchy);

        // Act
        List<CategoryDto> actualHierarchy = categoryQueryService.getAllActiveCategoryHierarchy();

        // Assert
        // Check if the service returned the exact list from the repository
        assertEquals(mockHierarchy, actualHierarchy);

        // Verify that the repository method was called exactly once
        verify(categoryReadRepository).findAllActiveCategoryHierarchy();
    }
}
