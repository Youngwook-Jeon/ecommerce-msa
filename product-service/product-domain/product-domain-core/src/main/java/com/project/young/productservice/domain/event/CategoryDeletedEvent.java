package com.project.young.productservice.domain.event;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.common.domain.valueobject.CategoryId;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class CategoryDeletedEvent implements DomainEvent<CategoryId> {

    private final CategoryId categoryId;
    private final String categoryName;
    private final List<CategoryId> deletedCategoryIds; // 함께 삭제된 하위 카테고리들
    private final Instant occurredAt;

    public CategoryDeletedEvent(CategoryId categoryId, String categoryName, List<CategoryId> deletedCategoryIds) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.deletedCategoryIds = deletedCategoryIds;
        this.occurredAt = Instant.now();
    }
}