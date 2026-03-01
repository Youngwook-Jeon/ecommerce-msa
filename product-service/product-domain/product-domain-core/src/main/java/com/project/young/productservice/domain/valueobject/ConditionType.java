package com.project.young.productservice.domain.valueobject;

public enum ConditionType {
    NEW("새 상품"),
    USED("중고"),
    REFURBISHED("리퍼"),
    OPEN_BOX("오픈박스");

    private final String description;

    ConditionType(String description) {
        this.description = description;
    }
}
