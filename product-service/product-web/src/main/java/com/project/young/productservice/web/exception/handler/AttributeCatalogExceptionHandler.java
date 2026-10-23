package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
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

@Slf4j
@ControllerAdvice(assignableTypes = AdminAttributeCatalogController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttributeCatalogExceptionHandler {

    @ResponseBody
    @ExceptionHandler(AttributeInactiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleInactiveAttribute(AttributeInactiveException exception) {
        log.warn("Attribute catalog operation rejected: code=ATTRIBUTE_INACTIVE, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("ATTRIBUTE_INACTIVE")
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(AttributeInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleAttributeInUse(AttributeInUseException exception) {
        log.warn("Attribute catalog operation rejected: code=ATTRIBUTE_IN_USE, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("ATTRIBUTE_IN_USE")
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(AttributeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO handleAttributeNotFound(AttributeNotFoundException exception) {
        log.warn("Attribute catalog resource not found: code=ATTRIBUTE_CATALOG_NOT_FOUND, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("ATTRIBUTE_CATALOG_NOT_FOUND")
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(AttributeDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleAttributeDomainException(AttributeDomainException exception) {
        log.warn("Invalid attribute catalog operation: code=INVALID_ATTRIBUTE_CATALOG_REQUEST, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder()
                .code("INVALID_ATTRIBUTE_CATALOG_REQUEST")
                .message(exception.getMessage())
                .build();
    }

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
