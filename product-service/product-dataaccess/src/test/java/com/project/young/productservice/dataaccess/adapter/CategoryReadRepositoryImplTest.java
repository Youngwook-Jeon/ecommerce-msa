package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.CategoryDto;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryReadRepositoryImplTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository; // Mock the actual JPA repository

    @InjectMocks
    private CategoryReadRepositoryImpl categoryReadRepository; // Test the implementation class

    @Test
    @DisplayName("Should build correct category hierarchy from flat entity list")
    void testFindActiveCategoryHierarchy_Success() {
        // Arrange: Prepare mock CategoryEntity objects
        CategoryEntity electronics = new CategoryEntity(1L, "Electronics", "ACTIVE", null, Collections.emptyList());
        CategoryEntity books = new CategoryEntity(2L, "Books", "ACTIVE", null, Collections.emptyList());
        CategoryEntity laptops = new CategoryEntity(11L, "Laptops", "ACTIVE", electronics, Collections.emptyList());

        List<CategoryEntity> flatEntityList = List.of(electronics, books, laptops);

        when(categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE)).thenReturn(flatEntityList);

        // Act
        List<CategoryDto> resultHierarchy = categoryReadRepository.findAllActiveCategoryHierarchy();

        // Assert
        assertNotNull(resultHierarchy);
        assertEquals(2, resultHierarchy.size(), "Should have 2 root categories");

        Optional<CategoryDto> electronicsDtoOpt = resultHierarchy.stream().filter(c -> c.id().equals(1L)).findFirst();
        assertTrue(electronicsDtoOpt.isPresent());
        CategoryDto electronicsDto = electronicsDtoOpt.get();
        assertEquals(1, electronicsDto.children().size(), "Electronics should have 1 child");
        assertEquals("Laptops", electronicsDto.children().getFirst().name());

        verify(categoryJpaRepository).findAllWithParentByStatus(Category.STATUS_ACTIVE);
    }
}