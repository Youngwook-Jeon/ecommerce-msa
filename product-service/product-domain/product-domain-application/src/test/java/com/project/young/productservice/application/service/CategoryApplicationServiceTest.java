package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResponse;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryApplicationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryDomainService categoryDomainService;
    @Mock
    private CategoryDataMapper categoryDataMapper;

    @InjectMocks
    private CategoryApplicationService categoryApplicationService;

    private CreateCategoryCommand createCommandWithParent;
    private CreateCategoryCommand createCommandWithoutParent;
    private CategoryId parentCategoryId;
    private CategoryId generatedCategoryId;
    private Category categoryToSave;
    private Category savedCategory;
    private CreateCategoryResponse expectedResponse;

    @BeforeEach
    void setUp() {
        parentCategoryId = new CategoryId(10L);
        generatedCategoryId = new CategoryId(1L);

        createCommandWithParent = CreateCategoryCommand.builder()
                .name("New Category With Parent")
                .parentId(parentCategoryId.getValue())
                .build();

        createCommandWithoutParent = CreateCategoryCommand.builder()
                .name("New Top Level Category")
                .parentId(null)
                .build();

        categoryToSave = Category.builder()
                .name(createCommandWithParent.getName())
                .parentId(parentCategoryId)
                .build();

        savedCategory = Category.builder()
                .name(createCommandWithParent.getName())
                .parentId(parentCategoryId)
                .build();

        expectedResponse = new CreateCategoryResponse(
                "New Category With Parent",
                "Category New Category With Parent created successfully."
        );
    }

    @Test
    @DisplayName("Create Category Success - With Parent")
    void testCreateCategory_Success_WithParent() {
        // Arrange
        when(categoryDomainService.isCategoryNameUnique(createCommandWithParent.getName())).thenReturn(true);
        when(categoryRepository.existsById(parentCategoryId)).thenReturn(true);
        when(categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)).thenReturn(true);
        when(categoryDataMapper.toCategory(createCommandWithParent, parentCategoryId)).thenReturn(categoryToSave);
        // Mock the repository saving the category and returning it with an ID
        // We need a 'saved' mock that returns the generated ID
        Category mockSavedCategoryWithId = mock(Category.class);
        when(mockSavedCategoryWithId.getId()).thenReturn(generatedCategoryId); // Mock getId()
        when(mockSavedCategoryWithId.getName()).thenReturn(createCommandWithParent.getName());
        when(categoryRepository.save(categoryToSave)).thenReturn(mockSavedCategoryWithId);

        when(categoryDataMapper.toCreateCategoryResponse(eq(mockSavedCategoryWithId), anyString())).thenReturn(expectedResponse);

        // Act
        CreateCategoryResponse actualResponse = categoryApplicationService.createCategory(createCommandWithParent);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(expectedResponse.name(), actualResponse.name());
        assertEquals(expectedResponse.message(), actualResponse.message());

        // Verify that necessary methods were called
        verify(categoryDomainService).isCategoryNameUnique(createCommandWithParent.getName());
        verify(categoryRepository).existsById(parentCategoryId);
        verify(categoryDomainService).isParentDepthLessThanLimit(parentCategoryId);
        verify(categoryDataMapper).toCategory(createCommandWithParent, parentCategoryId);
        verify(categoryRepository).save(categoryToSave);
        verify(categoryDataMapper).toCreateCategoryResponse(eq(mockSavedCategoryWithId), anyString());
    }

    @Test
    @DisplayName("Create Category Success - No Parent (Top Level)")
    void testCreateCategory_Success_NoParent() {
        // Arrange
        Category categoryToSaveNoParent = Category.builder().name(createCommandWithoutParent.getName()).parentId(null).build();
        Category mockSavedCategoryNoParent = mock(Category.class);
        CreateCategoryResponse expectedResponseNoParent = new CreateCategoryResponse(createCommandWithoutParent.getName(), "Category New Top Level Category created successfully.");

        when(categoryDomainService.isCategoryNameUnique(createCommandWithoutParent.getName())).thenReturn(true);
        when(categoryDataMapper.toCategory(createCommandWithoutParent, null)).thenReturn(categoryToSaveNoParent);
        when(mockSavedCategoryNoParent.getId()).thenReturn(generatedCategoryId);
        when(mockSavedCategoryNoParent.getName()).thenReturn(createCommandWithoutParent.getName());
        when(categoryRepository.save(categoryToSaveNoParent)).thenReturn(mockSavedCategoryNoParent);
        when(categoryDataMapper.toCreateCategoryResponse(eq(mockSavedCategoryNoParent), anyString())).thenReturn(expectedResponseNoParent);

        // Act
        CreateCategoryResponse actualResponse = categoryApplicationService.createCategory(createCommandWithoutParent);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(expectedResponseNoParent.name(), actualResponse.name());

        // Verify no parent checks were called
        verify(categoryRepository, never()).existsById(any(CategoryId.class));
        verify(categoryDomainService, never()).isParentDepthLessThanLimit(any(CategoryId.class));
        verify(categoryDataMapper).toCategory(createCommandWithoutParent, null);
        verify(categoryRepository).save(categoryToSaveNoParent);
        verify(categoryDataMapper).toCreateCategoryResponse(eq(mockSavedCategoryNoParent), anyString());
    }


    @Test
    @DisplayName("Create Category Failure - Duplicate Name")
    void testCreateCategory_Failure_DuplicateName() {
        // Arrange
        when(categoryDomainService.isCategoryNameUnique(createCommandWithParent.getName())).thenReturn(false); // Simulate duplicate name

        // Act & Assert
        DuplicateCategoryNameException exception = assertThrows(DuplicateCategoryNameException.class, () -> {
            categoryApplicationService.createCategory(createCommandWithParent);
        });

        // Optionally assert exception message
        assertTrue(exception.getMessage().contains("already exists"));

        // Verify that save was never called
        verify(categoryRepository, never()).save(any(Category.class));
        verify(categoryDataMapper, never()).toCategory(any(), any());
        verify(categoryDataMapper, never()).toCreateCategoryResponse(any(), anyString());
    }

    @Test
    @DisplayName("Create Category Failure - Parent Not Found")
    void testCreateCategory_Failure_ParentNotFound() {
        // Arrange
        when(categoryDomainService.isCategoryNameUnique(createCommandWithParent.getName())).thenReturn(true);
        when(categoryRepository.existsById(parentCategoryId)).thenReturn(false);

        // Act & Assert
        CategoryDomainException exception = assertThrows(CategoryDomainException.class, () -> {
            categoryApplicationService.createCategory(createCommandWithParent);
        });

        // Optionally assert exception message
        assertTrue(exception.getMessage().contains("Parent category with id"));
        assertTrue(exception.getMessage().contains("not found"));

        // Verify save was not called
        verify(categoryDomainService, never()).isParentDepthLessThanLimit(any(CategoryId.class)); // Depth check shouldn't happen
        verify(categoryRepository, never()).save(any(Category.class));
        verify(categoryDataMapper, never()).toCategory(any(), any());
    }

    @Test
    @DisplayName("Create Category Failure - Depth Limit Exceeded")
    void testCreateCategory_Failure_DepthLimitExceeded() {
        // Arrange
        when(categoryDomainService.isCategoryNameUnique(createCommandWithParent.getName())).thenReturn(true);
        when(categoryRepository.existsById(parentCategoryId)).thenReturn(true);
        when(categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)).thenReturn(false); // Simulate depth limit exceeded

        // Act & Assert
        CategoryDomainException exception = assertThrows(CategoryDomainException.class, () -> {
            categoryApplicationService.createCategory(createCommandWithParent);
        });

        // Optionally assert exception message
        assertTrue(exception.getMessage().contains("depth limit exceeded"));

        // Verify save was not called
        verify(categoryRepository, never()).save(any(Category.class));
        verify(categoryDataMapper, never()).toCategory(any(), any());
    }

    @Test
    @DisplayName("Create Category Failure - ID Not Assigned After Save")
    void testCreateCategory_Failure_SaveFails_IdNotAssigned() {
        // Arrange
        // Setup mocks similar to success case up to the save point
        when(categoryDomainService.isCategoryNameUnique(createCommandWithParent.getName())).thenReturn(true);
        when(categoryRepository.existsById(parentCategoryId)).thenReturn(true);
        when(categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)).thenReturn(true);
        when(categoryDataMapper.toCategory(createCommandWithParent, parentCategoryId)).thenReturn(categoryToSave);

        // Mock the repository returning a category WHERE getId() returns null
        Category mockSavedCategoryWithNullId = mock(Category.class);
        when(mockSavedCategoryWithNullId.getId()).thenReturn(null); // Simulate ID not being assigned
        when(categoryRepository.save(categoryToSave)).thenReturn(mockSavedCategoryWithNullId);

        // Act & Assert
        CategoryDomainException exception = assertThrows(CategoryDomainException.class, () -> {
            categoryApplicationService.createCategory(createCommandWithParent);
        });

        // Optionally assert exception message
        assertTrue(exception.getMessage().contains("Failed to assign ID"));

        // Verify save was called, but response mapping was not
        verify(categoryRepository).save(categoryToSave);
        verify(categoryDataMapper, never()).toCreateCategoryResponse(any(), anyString());
    }
}
