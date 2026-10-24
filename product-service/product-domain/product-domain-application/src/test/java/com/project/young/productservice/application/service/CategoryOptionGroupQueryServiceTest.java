package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryOptionGroupQueryServiceTest {
    @Mock
    AttributeCatalogPort catalog;
    @Mock
    CategoryOptionGroupReadRepository optionGroups;
    @InjectMocks
    CategoryOptionGroupQueryService service;

    @Test
    void returnsAvailableGroupsInRepositoryOrder() {
        var groups = List.of(new ReadCategoryOptionGroupView(UUID.randomUUID(), "RAM", "Memory",
                UUID.randomUUID(), "ram_capacity", "RAM capacity", AttributeDataType.NUMBER, "GB", true, 10));
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, false)));
        when(optionGroups.findAvailableByCategoryId(4L)).thenReturn(groups);

        assertThat(service.getAvailableOptionGroups(4L)).isEqualTo(groups);
    }

    @Test
    void returnsEmptyListForCategoryWithNoEligibleGroups() {
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, false)));
        when(optionGroups.findAvailableByCategoryId(4L)).thenReturn(List.of());

        assertThat(service.getAvailableOptionGroups(4L)).isEmpty();
    }

    @Test
    void rejectsMissingCategoryInsteadOfReturningAnEmptyList() {
        when(catalog.findCategory(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableOptionGroups(4L)).isInstanceOf(CategoryNotFoundException.class);
        verifyNoInteractions(optionGroups);
    }

    @Test
    void rejectsSoftDeletedCategory() {
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, true)));

        assertThatThrownBy(() -> service.getAvailableOptionGroups(4L)).isInstanceOf(CategoryNotFoundException.class);
        verifyNoInteractions(optionGroups);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidCategoryIds(Long categoryId) {
        assertThatThrownBy(() -> service.getAvailableOptionGroups(categoryId)).isInstanceOf(CategoryDomainException.class);
        verifyNoInteractions(catalog, optionGroups);
    }
}
