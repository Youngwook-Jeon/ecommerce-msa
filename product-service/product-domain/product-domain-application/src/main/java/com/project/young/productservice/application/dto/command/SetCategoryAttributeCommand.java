package com.project.young.productservice.application.dto.command;

public record SetCategoryAttributeCommand(boolean enabled, boolean required, boolean optionAllowed,
                                         boolean filterable, int displayOrder) {
}
