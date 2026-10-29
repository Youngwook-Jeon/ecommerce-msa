package com.project.young.productservice.application.exception;

import java.util.UUID;

public class OptionGroupVersionConflictException extends RuntimeException {
    public OptionGroupVersionConflictException(UUID groupId, long expectedVersion) {
        super("Option group version conflict: groupId=" + groupId + ", expectedVersion=" + expectedVersion);
    }
}
