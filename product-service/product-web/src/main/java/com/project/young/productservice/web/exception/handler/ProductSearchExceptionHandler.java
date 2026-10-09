package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.application.exception.ProductSearchCategoryNotFoundException;
import com.project.young.productservice.application.exception.ProductSearchRequestException;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.web.publicapi.controller.PublicProductSearchController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Search-specific error codes without changing other endpoints' error contracts. */
@ControllerAdvice(assignableTypes = PublicProductSearchController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ProductSearchExceptionHandler {

    @ResponseBody
    @ExceptionHandler(ProductSearchRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleInvalidRequest(ProductSearchRequestException exception) {
        log.debug("Invalid product search request: code={}", exception.code());
        return ErrorDTO.builder().code(exception.code().name()).message(exception.getMessage()).build();
    }

    @ResponseBody
    @ExceptionHandler(ProductSearchCategoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO handleCategoryNotFound(ProductSearchCategoryNotFoundException exception) {
        return ErrorDTO.builder().code("CATEGORY_NOT_FOUND").message(exception.getMessage()).build();
    }

    @ResponseBody
    @ExceptionHandler(ProductSearchUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorDTO handleUnavailable(ProductSearchUnavailableException exception) {
        log.warn("Product search unavailable: code=SEARCH_UNAVAILABLE, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder().code("SEARCH_UNAVAILABLE")
                .message("Product search is temporarily unavailable.").build();
    }

    @ResponseBody
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO handleException(Exception exception) {
        log.error("Unexpected product search failure: code=INTERNAL_ERROR, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder().code("INTERNAL_ERROR")
                .message("An unexpected error occurred.").build();
    }
}
