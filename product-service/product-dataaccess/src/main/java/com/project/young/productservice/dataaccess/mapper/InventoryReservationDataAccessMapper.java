package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationDataAccessMapper {

    public InventoryReservationEntity toEntity(InventoryReservation reservation) {
        return InventoryReservationEntity.builder()
                .id(reservation.getId().getValue())
                .checkoutId(reservation.getCheckoutId().getValue())
                .productVariantId(reservation.getProductVariantId().getValue())
                .quantity(reservation.getQuantity())
                .status(reservation.getStatus().name())
                .expiresAt(reservation.getExpiresAt())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    public void updateEntity(InventoryReservation reservation, InventoryReservationEntity entity) {
        entity.setStatus(reservation.getStatus().name());
        entity.setUpdatedAt(reservation.getUpdatedAt());
    }

    public InventoryReservation toDomain(InventoryReservationEntity entity) {
        return InventoryReservation.reconstitute(
                new InventoryReservationId(entity.getId()),
                new CheckoutId(entity.getCheckoutId()),
                new ProductVariantId(entity.getProductVariantId()),
                entity.getQuantity(),
                InventoryReservationStatus.fromString(entity.getStatus()),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
