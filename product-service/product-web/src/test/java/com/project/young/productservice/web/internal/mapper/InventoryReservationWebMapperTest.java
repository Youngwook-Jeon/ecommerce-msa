package com.project.young.productservice.web.internal.mapper;

import com.project.young.productservice.application.dto.command.ReserveInventoryCommand;
import com.project.young.productservice.application.dto.result.ReserveInventoryResult;
import com.project.young.productservice.web.internal.dto.ReserveInventoryRequest;
import com.project.young.productservice.web.internal.dto.ReserveInventoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationWebMapperTest {

    private final InventoryReservationWebMapper mapper = new InventoryReservationWebMapper();

    @Test
    @DisplayName("toCommand: request lines를 ReserveInventoryCommand로 매핑한다")
    void toCommand_mapsRequestToCommand() {
        UUID checkoutId = UUID.randomUUID();
        UUID firstVariantId = UUID.randomUUID();
        UUID secondVariantId = UUID.randomUUID();
        ReserveInventoryRequest request = new ReserveInventoryRequest(
                checkoutId,
                List.of(
                        new ReserveInventoryRequest.Line(firstVariantId, 2),
                        new ReserveInventoryRequest.Line(secondVariantId, 1)
                )
        );

        ReserveInventoryCommand command = mapper.toCommand(request);

        assertThat(command.checkoutId()).isEqualTo(checkoutId);
        assertThat(command.lines()).containsExactly(
                new ReserveInventoryCommand.ReserveInventoryLine(firstVariantId, 2),
                new ReserveInventoryCommand.ReserveInventoryLine(secondVariantId, 1)
        );
    }

    @Test
    @DisplayName("toResponse: result lines를 ReserveInventoryResponse로 매핑한다")
    void toResponse_mapsResultToResponse() {
        UUID checkoutId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-15T10:15:00Z");
        ReserveInventoryResult result = new ReserveInventoryResult(
                checkoutId,
                expiresAt,
                List.of(new ReserveInventoryResult.Line(reservationId, variantId, 3, "ACTIVE")),
                true
        );

        ReserveInventoryResponse response = mapper.toResponse(result);

        assertThat(response.checkoutId()).isEqualTo(checkoutId);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.reusedExisting()).isTrue();
        assertThat(response.lines()).containsExactly(
                new ReserveInventoryResponse.Line(reservationId, variantId, 3, "ACTIVE")
        );
    }
}
