package com.project.young.productservice.web.message;

import org.springframework.stereotype.Component;

@Component
public class ProductImageResponseMessageFactory {

    public String imageCommitted() {
        return "Image committed successfully";
    }

    public String imagesReordered() {
        return "Images reordered successfully";
    }
}
