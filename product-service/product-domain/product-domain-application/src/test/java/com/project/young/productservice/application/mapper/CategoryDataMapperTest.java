package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResponse;
import com.project.young.productservice.domain.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryDataMapperTest {

    private CategoryDataMapper categoryDataMapper;

    @BeforeEach
    void setUp() {
        categoryDataMapper = new CategoryDataMapper();
    }

    @Test
    @DisplayName("Should map Category and message to CreateCategoryResponse")
    void testToCreateCategoryResponse() {
        // Arrange
        Category mockCategory = mock(Category.class);
        when(mockCategory.getName()).thenReturn("Test Category Name");
        String message = "Category successfully created.";

        // Act
        CreateCategoryResponse response = categoryDataMapper.toCreateCategoryResponse(mockCategory, message);

        // Assert
        assertNotNull(response);
        assertEquals("Test Category Name", response.name());
        assertEquals(message, response.message());
    }

    @Test
    @DisplayName("Should map CreateCategoryCommand with parentId to Category")
    void testToCategory_WithParentId() {
        // Arrange
        long parentIdValue = 10L;
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name("Child Category")
                .parentId(parentIdValue) // Command DTO holds the raw Long ID
                .build();
        CategoryId parentCategoryId = new CategoryId(parentIdValue); // Mapper receives the Value Object

        // Act
        Category category = categoryDataMapper.toCategory(command, parentCategoryId);

        // Assert
        assertNotNull(category);
        assertEquals("Child Category", category.getName());
        assertTrue(category.getParentId().isPresent(), "ParentId Optional should be present");
        assertEquals(parentCategoryId, category.getParentId().get(), "ParentId should match");
        // Assuming ID is not set by this mapper
        assertNull(category.getId(), "ID should be null as it's typically set upon saving");
    }

    @Test
    @DisplayName("Should map CreateCategoryCommand with null parentId to Category")
    void testToCategory_WithNullParentId() {
        // Arrange
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name("Top Level Category")
                .parentId(null)
                .build();

        // Act
        Category category = categoryDataMapper.toCategory(command, null);

        // Assert
        assertNotNull(category);
        assertEquals("Top Level Category", category.getName());
        assertTrue(category.getParentId().isEmpty(), "ParentId Optional should be empty");
        // Assuming ID is not set by this mapper
        assertNull(category.getId(), "ID should be null as it's typically set upon saving");
    }
}
