package com.project.young.productservice.application.exception;

public class ProductSearchCategoryNotFoundException extends RuntimeException {

    public ProductSearchCategoryNotFoundException(long categoryId) {
        super("Category not found or not active: " + categoryId);
    }
}
