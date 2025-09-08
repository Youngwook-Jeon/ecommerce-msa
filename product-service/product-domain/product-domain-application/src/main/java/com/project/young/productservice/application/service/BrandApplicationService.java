package com.project.young.productservice.application.service;

import com.project.young.common.domain.event.publisher.DomainEventPublisher;
import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.domain.entity.Brand;
import com.project.young.productservice.domain.event.BrandDeletedEvent;
import com.project.young.productservice.domain.event.BrandStatusChangedEvent;
import com.project.young.productservice.domain.exception.BrandDomainException;
import com.project.young.productservice.domain.repository.BrandRepository;
import com.project.young.productservice.domain.service.BrandDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final BrandDomainService brandDomainService;
    private final BrandDataMapper brandDataMapper;
    private final DomainEventPublisher domainEventPublisher;

    public BrandApplicationService(BrandRepository brandRepository,
                                   BrandDomainService brandDomainService,
                                   BrandDataMapper brandDataMapper,
                                   DomainEventPublisher domainEventPublisher) {
        this.brandRepository = brandRepository;
        this.brandDomainService = brandDomainService;
        this.brandDataMapper = brandDataMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CreateBrandResponse createBrand(CreateBrandCommand command) {
        log.info("Attempting to create brand with name: {}", command.getName());

        if (!brandDomainService.isBrandNameUnique(command.getName())) {
            log.warn("Brand name already exists: {}", command.getName());
            throw new DuplicateBrandNameException("Brand name '" + command.getName() + "' already exists.");
        }

        Brand newBrand = brandDataMapper.toBrand(command);
        Brand savedBrand = persistBrand(newBrand);

        log.info("Brand saved successfully with id: {}", savedBrand.getId().getValue());
        return brandDataMapper.toCreateBrandResponse(savedBrand,
                "Brand " + savedBrand.getName() + " created successfully.");
    }

    @Transactional
    public UpdateBrandResponse updateBrand(Long brandIdValue, UpdateBrandCommand command) {
        validateUpdateRequest(brandIdValue);

        BrandId brandId = new BrandId(brandIdValue);
        log.info("Attempting to update brand with id: {}", brandId.getValue());

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new BrandNotFoundException("Brand with id " + brandId.getValue() + " not found."));
        validateBrandCanBeUpdated(brand);

        boolean hasChanges = performUpdates(brand, command, brandId);

        String message;
        if (hasChanges) {
            brandRepository.save(brand);
            message = "Brand '" + brand.getName() + "' updated successfully.";
        } else {
            message = "Brand '" + brand.getName() + "' was not changed.";
        }

        return brandDataMapper.toUpdateBrandResponse(brand, message);
    }

    @Transactional
    public DeleteBrandResponse deleteBrand(Long brandIdValue) {
        validateDeleteRequest(brandIdValue);

        BrandId brandId = new BrandId(brandIdValue);
        log.info("Attempting to soft-delete brand with id: {}", brandId.getValue());

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new BrandNotFoundException("Brand with id " + brandId.getValue() + " not found."));

        if (brand.isDeleted()) {
            throw new BrandDomainException("Brand is already deleted.");
        }

        String brandName = brand.getName();
        brand.markAsDeleted();
        brandRepository.save(brand);

        // 삭제 이벤트 발행
        BrandDeletedEvent deletionEvent = new BrandDeletedEvent(brandId, brandName);
        domainEventPublisher.publishEventAfterCommit(deletionEvent);

        // 상태 변경 이벤트도 발행
        BrandStatusChangedEvent statusEvent = new BrandStatusChangedEvent(
                brandId, Brand.STATUS_ACTIVE, Brand.STATUS_DELETED
        );
        domainEventPublisher.publishEventAfterCommit(statusEvent);

        log.info("Brand marked as deleted: {}", brandName);

        return new DeleteBrandResponse(brandId.getValue(),
                "Brand " + brandName + " (ID: " + brandId.getValue() + ") marked as deleted successfully.");
    }

    @Transactional
    public void updateBrandsStatus(List<BrandId> brandIds, String newStatus) {
        log.info("Updating status for {} brands to: {}", brandIds.size(), newStatus);

        List<Brand> brands = brandRepository.findAllById(brandIds);

        if (brands.size() != brandIds.size()) {
            throw new IllegalArgumentException("Some brands not found");
        }

        // 현재 상태 기록
        List<StatusChangeRecord> statusChanges = brands.stream()
                .map(brand -> new StatusChangeRecord(brand.getId(), brand.getStatus(), newStatus))
                .filter(record -> !record.oldStatus().equals(record.newStatus()))
                .toList();

        if (statusChanges.isEmpty()) {
            log.info("No brands need status update - all are already in status: {}", newStatus);
            return;
        }

        brandDomainService.validateStatusChangeRules(brands, newStatus);
        brandDomainService.processStatusChange(brands, newStatus);

        brandRepository.saveAll(brands);

        // 이벤트 발행
        statusChanges.forEach(change -> {
            BrandStatusChangedEvent event = new BrandStatusChangedEvent(
                    change.brandId(), change.oldStatus(), change.newStatus()
            );
            domainEventPublisher.publishEventAfterCommit(event);
        });

        log.info("Successfully updated status for {} brands and published {} events",
                brands.size(), statusChanges.size());
    }

    private void validateUpdateRequest(Long brandIdValue) {
        if (brandIdValue == null) {
            throw new IllegalArgumentException("Brand ID for update cannot be null.");
        }
    }

    private void validateDeleteRequest(Long brandIdValue) {
        if (brandIdValue == null) {
            log.warn("Attempted to delete brand with a null ID value.");
            throw new IllegalArgumentException("Brand ID for delete cannot be null.");
        }
    }

    private void validateBrandCanBeUpdated(Brand brand) {
        if (brand.isDeleted()) {
            throw new BrandDomainException("Cannot update a brand that has been deleted.");
        }
    }

    private boolean performUpdates(Brand brand, UpdateBrandCommand command, BrandId brandId) {
        boolean nameChanged = applyNameChange(brand, command.getName(), brandId);
        boolean logoUrlChanged = applyLogoUrlChange(brand, command.getLogoUrl());
        boolean statusChanged = applyStatusChange(brand, command.getStatus());

        return nameChanged || logoUrlChanged || statusChanged;
    }

    private boolean applyNameChange(Brand brand, String newName, BrandId brandId) {
        if (newName != null && !Objects.equals(brand.getName(), newName)) {
            if (!brandDomainService.isBrandNameUniqueForUpdate(newName, brandId)) {
                throw new DuplicateBrandNameException("Brand name '" + newName + "' already exists.");
            }
            brand.changeName(newName);
            return true;
        }
        return false;
    }

    private boolean applyLogoUrlChange(Brand brand, String newLogoUrl) {
        if (newLogoUrl != null && !Objects.equals(brand.getLogoUrl(), newLogoUrl)) {
            brand.changeLogoUrl(newLogoUrl);
            return true;
        }
        return false;
    }

    private boolean applyStatusChange(Brand brand, String newStatus) {
        if (newStatus != null && !Objects.equals(brand.getStatus(), newStatus)) {
            String oldStatus = brand.getStatus();

            // 도메인 서비스로 검증
            brandDomainService.validateStatusChangeRules(List.of(brand), newStatus);

            brand.changeStatus(newStatus);

            // 상태 변경 이벤트 발행
            BrandStatusChangedEvent event = new BrandStatusChangedEvent(
                    brand.getId(), oldStatus, newStatus
            );
            domainEventPublisher.publishEventAfterCommit(event);

            return true;
        }
        return false;
    }

    private Brand persistBrand(Brand brand) {
        Brand savedBrand = brandRepository.save(brand);
        if (savedBrand.getId() == null) {
            log.error("Brand ID was not assigned after save for name: {}", savedBrand.getName());
            throw new BrandDomainException("Failed to assign ID to the new brand.");
        }
        return savedBrand;
    }

    // 상태 변경 기록용 레코드
    private record StatusChangeRecord(BrandId brandId, String oldStatus, String newStatus) {}
}
