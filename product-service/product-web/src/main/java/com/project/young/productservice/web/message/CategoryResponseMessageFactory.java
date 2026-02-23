package com.project.young.productservice.web.message;

import org.springframework.stereotype.Component;

@Component
public class CategoryResponseMessageFactory {

    public String categoryCreated() {
        return "Category created successfully";
    }

    public String categoryUpdated() {
        return "Category updated successfully";
    }

    public String categoryDeleted() {
        return "Category deleted successfully";
    }
}