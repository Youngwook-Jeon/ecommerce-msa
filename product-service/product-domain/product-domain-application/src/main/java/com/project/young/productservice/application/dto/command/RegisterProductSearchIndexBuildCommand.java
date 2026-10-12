package com.project.young.productservice.application.dto.command;

public record RegisterProductSearchIndexBuildCommand(String requestedBy, String idempotencyKey,
                                                     int batchSize, boolean catalogWritesPaused) {
}
