package com.project.young.productservice.web.internal.mapper;

import com.project.young.productservice.application.dto.command.ReserveInventoryCommand;
import com.project.young.productservice.application.dto.result.ReserveInventoryResult;
import com.project.young.productservice.web.internal.dto.ReserveInventoryRequest;
import com.project.young.productservice.web.internal.dto.ReserveInventoryResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationWebMapper {

    public ReserveInventoryCommand toCommand(ReserveInventoryRequest request) {
        return new ReserveInventoryCommand(
                request.checkoutId(),
                request.lines().stream()
                        .map(line -> new ReserveInventoryCommand.ReserveInventoryLine(
                                line.productVariantId(),
                                line.quantity()
                        ))
                        .toList()
        );
    }

    public ReserveInventoryResponse toResponse(ReserveInventoryResult result) {
        return new ReserveInventoryResponse(
                result.checkoutId(),
                result.expiresAt(),
                result.reusedExisting(),
                result.lines().stream()
                        .map(line -> new ReserveInventoryResponse.Line(
                                line.reservationId(),
                                line.productVariantId(),
                                line.quantity(),
                                line.status()
                        ))
                        .toList()
        );
    }
}
