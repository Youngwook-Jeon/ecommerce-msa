package com.project.young.productservice.domain.valueobject;

public enum ConditionType {
    NEW("New"),
    USED("Used"),
    REFURBISHED("Refurbished"),
    OPEN_BOX("Open box");

    private final String description;

    ConditionType(String description) {
        this.description = description;
    }
}
