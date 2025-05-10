package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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
        log.warn(productDomainException.getMessage(), productDomainException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(productDomainException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {ProductAlreadyExistsException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleException(ProductAlreadyExistsException productAlreadyExistsException) {
        log.warn(productAlreadyExistsException.getMessage(), productAlreadyExistsException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(productAlreadyExistsException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {DuplicateCategoryNameException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleException(DuplicateCategoryNameException duplicateCategoryNameException) {
        log.warn(duplicateCategoryNameException.getMessage(), duplicateCategoryNameException);
        return ErrorDTO.builder()
                .code(HttpStatus.CONFLICT.getReasonPhrase())
                .message(duplicateCategoryNameException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {CategoryDomainException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleException(CategoryDomainException categoryDomainException) {
        log.warn(categoryDomainException.getMessage(), categoryDomainException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(categoryDomainException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {CategoryNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleException(CategoryNotFoundException categoryNotFoundException) {
        log.warn(categoryNotFoundException.getMessage(), categoryNotFoundException);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(categoryNotFoundException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorDTO handleAccessDeniedException(AccessDeniedException accessDeniedException) {
        log.warn(accessDeniedException.getMessage(), accessDeniedException);
        return ErrorDTO.builder()
                .code(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(accessDeniedException.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(value = {AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDTO handleAuthenticationException(AuthenticationException authenticationException) {
        log.warn(authenticationException.getMessage(), authenticationException);
        return ErrorDTO.builder()
                .code(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(authenticationException.getMessage())
                .build();
    }
}
