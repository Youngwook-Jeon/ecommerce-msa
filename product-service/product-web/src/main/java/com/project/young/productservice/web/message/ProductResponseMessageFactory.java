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

    public String productVariantUpdated() {
        return "Product variant updated successfully";
    }

    public String productVariantDeleted() {
        return "Product variant deleted successfully";
    }

    public String productOptionGroupDeleted() {
        return "Product option group deleted successfully";
    }

    public String productOptionValueDeleted() {
        return "Product option value deleted successfully";
    }

    public String productOptionGroupStepOrderUpdated() {
        return "Product option group step order updated successfully";
    }

    public String productOptionGroupsReordered() {
        return "Product option groups reordered successfully";
    }
}
