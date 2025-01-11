package com.project.young.productservice;

import com.project.young.common.domain.util.IdentityGenerator;
import com.project.young.common.domain.valueobject.ProductID;

import java.util.UUID;

public class ProductIDGenerator implements IdentityGenerator<ProductID> {

    @Override
    public ProductID generateID() {
        return new ProductID(UUID.randomUUID());
    }
}
