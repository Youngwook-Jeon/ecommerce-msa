package com.project.young.productservice.domain.ports.input.service;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;

public interface ProductApplicationService {

    CreateProductResponse createProduct(CreateProductCommand createProductCommand);
}
