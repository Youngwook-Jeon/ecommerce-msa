package com.project.young.orderservice.dataaccess.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestCartDocument {

    private UUID id;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private List<GuestCartItemDocument> items = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestCartItemDocument {

        private UUID id;
        private UUID productId;
        private UUID productVariantId;
        private String productName;
        private String brand;
        private String sku;
        private String imageUrl;
        private BigDecimal unitPrice;

        @Builder.Default
        private List<GuestCartOptionLineDocument> variantOptions = new ArrayList<>();

        private int quantity;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestCartOptionLineDocument {

        private int stepOrder;
        private UUID productOptionGroupId;
        private String optionGroupName;
        private UUID productOptionValueId;
        private String optionValueName;
    }
}
