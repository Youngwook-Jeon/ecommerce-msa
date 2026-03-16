package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.AdminProductDetailQuery;
import com.project.young.productservice.application.dto.AdminProductDetailResult;
import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminProductQueryService {

    private final AdminProductReadRepository adminProductReadRepository;

    public AdminProductQueryService(AdminProductReadRepository adminProductReadRepository) {
        this.adminProductReadRepository = adminProductReadRepository;
    }

    public AdminProductDetailResult getProductDetail(AdminProductDetailQuery query) {
        return adminProductReadRepository.getProductDetail(query);
    }

    public AdminProductReadRepository.AdminProductSearchResult search(AdminProductSearchCondition condition,
                                                                      int page,
                                                                      int size,
                                                                      String sortProperty,
                                                                      boolean ascending) {
        return adminProductReadRepository.search(condition, page, size, sortProperty, ascending);
    }
}

