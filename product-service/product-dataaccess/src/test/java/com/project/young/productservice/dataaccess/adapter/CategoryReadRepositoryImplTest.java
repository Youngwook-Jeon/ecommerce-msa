package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryReadRepositoryImplTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    @Mock
    private CategoryDataAccessMapper categoryDataAccessMapper;

    @InjectMocks
    private CategoryReadRepositoryImpl categoryReadRepository;

    @Test
    @DisplayName("Should build correct category hierarchy from flat entity list")
    void testFindActiveCategoryHierarchy_Success() {
        // Arrange: Prepare mock CategoryEntity objects
        Instant now = Instant.now();

        CategoryEntity electronics = CategoryEntity.builder()
                .id(1L)
                .name("Electronics")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .parent(null)
                .children(new ArrayList<>())
                .products(new ArrayList<>())
                .build();

        CategoryEntity books = CategoryEntity.builder()
                .id(2L)
                .name("Books")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .parent(null)
                .children(new ArrayList<>())
                .products(new ArrayList<>())
                .build();

        CategoryEntity laptops = CategoryEntity.builder()
                .id(11L)
                .name("Laptops")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .parent(electronics)
                .children(new ArrayList<>())
                .products(new ArrayList<>())
                .build();

        electronics.addChild(laptops);

        List<CategoryEntity> flatEntityList = List.of(electronics, books, laptops);

        when(categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE))
                .thenReturn(flatEntityList);

        when(categoryDataAccessMapper.toDomainStatus(CategoryStatusEntity.ACTIVE))
                .thenReturn(CategoryStatus.ACTIVE);

        // Act
        List<ReadCategoryView> resultHierarchy = categoryReadRepository.findAllActiveCategoryHierarchy();

        // Assert
        assertNotNull(resultHierarchy, "Result hierarchy should not be null");
        assertEquals(2, resultHierarchy.size(), "Should have 2 root categories");

        Optional<ReadCategoryView> electronicsDtoOpt = resultHierarchy.stream()
                .filter(c -> c.id().equals(1L))
                .findFirst();
        assertTrue(electronicsDtoOpt.isPresent(), "Electronics category should exist");

        ReadCategoryView electronicsDto = electronicsDtoOpt.get();
        assertEquals("Electronics", electronicsDto.name(), "Name should be Electronics");
        assertEquals(CategoryStatus.ACTIVE, electronicsDto.status(), "Status should be ACTIVE");
        assertEquals(1, electronicsDto.children().size(), "Electronics should have 1 child");
        assertEquals("Laptops", electronicsDto.children().getFirst().name(), "Child name should be Laptops");

        verify(categoryJpaRepository).findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);
    }

    @Test
    @DisplayName("Should return empty list when no active categories exist")
    void testFindActiveCategoryHierarchy_EmptyList() {
        // Arrange
        when(categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE))
                .thenReturn(List.of());

        // Act
        List<ReadCategoryView> result = categoryReadRepository.findAllActiveCategoryHierarchy();

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty");
        verify(categoryJpaRepository).findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);
    }

    @Test
    @DisplayName("Should correctly handle category with null parent")
    void testFindActiveCategoryHierarchy_NullParent() {
        // Arrange
        Instant now = Instant.now();

        CategoryEntity rootCategory = CategoryEntity.builder()
                .id(1L)
                .name("Root")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .parent(null)
                .children(new ArrayList<>())
                .products(new ArrayList<>())
                .build();

        when(categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE))
                .thenReturn(List.of(rootCategory));

        when(categoryDataAccessMapper.toDomainStatus(CategoryStatusEntity.ACTIVE))
                .thenReturn(CategoryStatus.ACTIVE);

        // Act
        List<ReadCategoryView> result = categoryReadRepository.findAllActiveCategoryHierarchy();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.getFirst().parentId(), "Parent ID should be null for root category");
        assertEquals(CategoryStatus.ACTIVE, result.getFirst().status());
    }
}