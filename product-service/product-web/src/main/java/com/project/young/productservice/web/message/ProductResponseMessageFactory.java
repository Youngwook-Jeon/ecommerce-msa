package com.project.young.productservice.web.message;

import org.springframework.stereotype.Component;

@Component
public class ProductResponseMessageFactory {

    public String productCreated() {
        return "Product created successfully";
    }

    public String productUpdated() {
        return "Product updated successfully";
    }

    public String productDeleted() {
        return "Product deleted successfully";
    }
}
