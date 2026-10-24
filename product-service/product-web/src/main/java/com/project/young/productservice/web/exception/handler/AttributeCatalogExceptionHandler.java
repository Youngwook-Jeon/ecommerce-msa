package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.web.controller.AdminAttributeCatalogController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Handles request, persistence and consistency errors specific to the attribute catalog API. */
@Slf4j
@ControllerAdvice(assignableTypes = AdminAttributeCatalogController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AttributeCatalogExceptionHandler {

    @ResponseBody
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleInvalidRequest(Exception exception) {
        log.debug("Invalid attribute catalog request: code=INVALID_ATTRIBUTE_CATALOG_REQUEST, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("INVALID_ATTRIBUTE_CATALOG_REQUEST")
                .message("Invalid attribute catalog request")
                .build();
    }

    @ResponseBody
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Attribute catalog persistence conflict: code=ATTRIBUTE_CATALOG_CONFLICT, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("ATTRIBUTE_CATALOG_CONFLICT")
                .message("The attribute code or value already exists, a reference prevents deletion, or a concurrent change conflicts")
                .build();
    }

    @ResponseBody
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO handleIllegalState(IllegalStateException exception) {
        log.error("Attribute catalog consistency failure: code=INTERNAL_ERROR, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred.")
                .build();
    }
}
