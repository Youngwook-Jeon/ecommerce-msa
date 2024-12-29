package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.domain.exception.ProductAlreadyExistsException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
@ControllerAdvice
public class ProductServiceGlobalExceptionHandler extends GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler(value = {ProductDomainException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleException(ProductDomainException productDomainException) {
        log.error(productDomainException.getMessage(), productDomainException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(productDomainException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {ProductAlreadyExistsException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleException(ProductAlreadyExistsException productAlreadyExistsException) {
        log.error(productAlreadyExistsException.getMessage(), productAlreadyExistsException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(productAlreadyExistsException.getMessage())
                .build();
    }
}
