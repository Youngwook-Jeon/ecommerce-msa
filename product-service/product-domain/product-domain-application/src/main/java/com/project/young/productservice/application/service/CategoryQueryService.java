package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.CategoryDto;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryQueryService {

    private final CategoryReadRepository categoryReadRepository;

    public CategoryQueryService(CategoryReadRepository categoryReadRepository) {
        this.categoryReadRepository = categoryReadRepository;
    }

    public List<CategoryDto> getAllActiveCategoryHierarchy() {
        return categoryReadRepository.findAllActiveCategoryHierarchy();
    }

    public List<CategoryDto> getAdminCategoryHierarchy() {
        return categoryReadRepository.findAllCategoryHierarchy();
    }
}
