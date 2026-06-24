package com.project.young.orderservice.dataaccess.entity;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CartItemOptionLineJson {

    private int stepOrder;
    private UUID productOptionGroupId;
    private String optionGroupName;
    private UUID productOptionValueId;
    private String optionValueName;
}
