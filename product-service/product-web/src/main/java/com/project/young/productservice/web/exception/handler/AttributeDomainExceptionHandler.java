package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.web.controller.AdminAttributeCatalogController;
import com.project.young.productservice.web.controller.OptionGroupController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Shares only attribute domain errors between attribute catalog and option dictionary APIs. */
@Slf4j
@ControllerAdvice(assignableTypes = {AdminAttributeCatalogController.class, OptionGroupController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttributeDomainExceptionHandler {

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

}
