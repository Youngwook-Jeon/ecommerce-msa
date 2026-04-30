package com.project.young.productservice.application.dto.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeProductOptionGroupStepOrderCommand {

    @Builder.Default
    private PlacementMode placementMode = PlacementMode.ABSOLUTE;
    private Double stepOrder;
    private UUID anchorProductOptionGroupId;

    public enum PlacementMode {
        ABSOLUTE,
        APPEND,
        PREPEND,
        BEFORE,
        AFTER
    }
}

