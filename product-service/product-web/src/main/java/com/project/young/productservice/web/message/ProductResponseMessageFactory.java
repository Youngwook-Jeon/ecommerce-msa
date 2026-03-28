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

    public String productStatusUpdated() {
        return "Product status updated successfully";
    }

    public String productDeleted() {
        return "Product deleted successfully";
    }

    public String productOptionGroupAdded() {
        return "Product option group added successfully";
    }

    public String productOptionValueAdded() {
        return "Product option value added successfully";
    }

    public String productVariantAdded() {
        return "Product variant added successfully";
    }
}
