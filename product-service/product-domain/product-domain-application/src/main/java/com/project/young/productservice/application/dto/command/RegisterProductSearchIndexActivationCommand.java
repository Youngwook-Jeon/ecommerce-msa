package com.project.young.productservice.application.dto.command;

import java.util.UUID;

public record RegisterProductSearchIndexActivationCommand(UUID indexVersionId, UUID expectedActiveVersionId,
                                                          String requestedBy, String idempotencyKey, boolean catalogWritesPaused) {
}
