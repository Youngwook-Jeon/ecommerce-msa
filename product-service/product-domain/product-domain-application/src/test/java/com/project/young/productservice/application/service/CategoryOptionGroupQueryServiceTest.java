package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
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
    @Test
    void batchesSelectedGroupsAndValuesAndPreservesEmptyGroupsInPolicyOrder() {
        var first = group();
        var second = group();
        var empty = group();
        var firstValue = value(first, 1);
        var secondValue = value(second, 2);
        Set<UUID> ids = Set.of(first.id(), second.id(), empty.id());
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, false)));
        when(optionGroups.findAvailableByCategoryIdAndIds(4L, ids)).thenReturn(List.of(second, first, empty));
        when(optionGroups.findActiveValuesByGroupIds(ids)).thenReturn(List.of(firstValue, secondValue));

        var results = service.getAvailableOptionValues(4L, List.of(first.id(), second.id(), empty.id(), first.id()));

        assertThat(results).extracting(result -> result.group().id()).containsExactly(second.id(), first.id(), empty.id());
        assertThat(results.get(0).optionValues()).containsExactly(secondValue);
        assertThat(results.get(1).optionValues()).containsExactly(firstValue);
        assertThat(results.get(2).optionValues()).isEmpty();
        verify(optionGroups).findAvailableByCategoryIdAndIds(4L, ids);
        verify(optionGroups).findActiveValuesByGroupIds(ids);
        verify(optionGroups, never()).findAvailableByCategoryId(any());
    }

    @Test
    void rejectsTheWholeRequestWhenAnyGroupIsUnavailable() {
        var available = group();
        UUID unavailable = UUID.randomUUID();
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, false)));
        when(optionGroups.findAvailableByCategoryIdAndIds(4L, Set.of(available.id(), unavailable)))
                .thenReturn(List.of(available));

        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, List.of(available.id(), unavailable)))
                .isInstanceOf(OptionGroupNotFoundException.class);
        verify(optionGroups, never()).findActiveValuesByGroupIds(any());
    }

    @Test
    void rejectsInvalidGroupSelectionsBeforeQueries() {
        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, null)).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, List.of())).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, Collections.singletonList(null)))
                .isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, Collections.nCopies(11, UUID.randomUUID())))
                .isInstanceOf(OptionDomainException.class);
        verifyNoInteractions(catalog, optionGroups);
    }

    @Test
    void rejectsBulkValuesForDeletedCategory() {
        when(catalog.findCategory(4L)).thenReturn(Optional.of(new AttributeCategoryView(4L, true)));
        assertThatThrownBy(() -> service.getAvailableOptionValues(4L, List.of(UUID.randomUUID())))
                .isInstanceOf(CategoryNotFoundException.class);
        verifyNoInteractions(optionGroups);
    }

    private ReadCategoryOptionGroupView group() {
        return new ReadCategoryOptionGroupView(UUID.randomUUID(), "RAM", "Memory", UUID.randomUUID(),
                "ram_capacity", "Memory capacity", AttributeDataType.NUMBER, "GB", true, 10);
    }

    private ReadCategoryOptionValueView value(ReadCategoryOptionGroupView group, int order) {
        return new ReadCategoryOptionValueView(group.id(), UUID.randomUUID(), "RAM" + order, "Memory choice", order,
                UUID.randomUUID(), group.attributeId(), AttributeDataType.NUMBER, "Canonical memory value",
                null, BigDecimal.valueOf(order), null, null);
    }

}
