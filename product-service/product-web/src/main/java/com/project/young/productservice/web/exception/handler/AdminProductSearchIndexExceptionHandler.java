package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.web.controller.AdminProductSearchIndexController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = AdminProductSearchIndexController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminProductSearchIndexExceptionHandler {
    @ExceptionHandler(ProductSearchIndexManagementException.class)
    public ResponseEntity<ErrorDTO> handleManagement(ProductSearchIndexManagementException exception) {
        HttpStatus status = switch (exception.code()) {
            case INVALID_INDEX_MANAGEMENT_REQUEST -> HttpStatus.BAD_REQUEST;
            case INDEX_BUILD_NOT_FOUND, INDEX_VERSION_NOT_FOUND, INDEX_ACTIVATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INDEX_BUILD_IN_PROGRESS, IDEMPOTENCY_KEY_CONFLICT, INDEX_ACTIVATION_IN_PROGRESS,
                    INDEX_NOT_READY, ACTIVE_INDEX_CHANGED, INDEX_STATE_UNCONFIRMED -> HttpStatus.CONFLICT;
            case INDEX_MANAGEMENT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(ErrorDTO.builder().code(exception.code().name()).message(exception.getMessage()).build());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorDTO> handlePersistenceFailure(Exception exception) {
        log.warn("Index management transaction failed: exceptionType={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorDTO.builder()
                .code("INDEX_MANAGEMENT_UNAVAILABLE").message("Index management is unavailable").build());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorDTO> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ErrorDTO.builder().code("INVALID_INDEX_MANAGEMENT_REQUEST")
                .message("Invalid index management request").build());
    }
}
