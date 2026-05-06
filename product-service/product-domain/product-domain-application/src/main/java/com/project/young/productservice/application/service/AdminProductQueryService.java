package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.query.AdminProductDetailQuery;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.dto.condition.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<ReadProductVariantView> getProductVariants(AdminProductDetailQuery query) {
        return adminProductReadRepository.getProductVariants(query);
    }

    public AdminProductReadRepository.AdminProductSearchResult search(AdminProductSearchCondition condition,
                                                                      int page,
                                                                      int size,
                                                                      String sortProperty,
                                                                      boolean ascending) {
        return adminProductReadRepository.search(condition, page, size, sortProperty, ascending);
    }
}

