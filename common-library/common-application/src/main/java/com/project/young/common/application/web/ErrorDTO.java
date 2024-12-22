package com.project.young.common.application.web;

import lombok.Builder;

@Builder
public record ErrorDTO(String code, String message) {
}
