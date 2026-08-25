package com.project.young.paymentservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.paymentservice.application.exception.InvalidStripeWebhookException;
import com.project.young.paymentservice.domain.exception.PaymentClientSecretNotReadyException;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentNotFoundException;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
@ControllerAdvice
public class PaymentServiceGlobalExceptionHandler extends GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO handlePaymentNotFound(PaymentNotFoundException exception) {
        // Expected while FE polls before order.created → payment is created.
        log.debug(exception.getMessage());
        return ErrorDTO.builder()
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(PaymentClientSecretNotReadyException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO handleClientSecretNotReady(PaymentClientSecretNotReadyException exception) {
        log.debug(exception.getMessage());
        return ErrorDTO.builder()
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(PaymentStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handlePaymentStateConflict(PaymentStateConflictException exception) {
        log.warn(exception.getMessage(), exception);
        return ErrorDTO.builder()
                .code(HttpStatus.CONFLICT.getReasonPhrase())
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(PaymentDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handlePaymentDomainException(PaymentDomainException exception) {
        log.warn(exception.getMessage(), exception);
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(exception.getMessage())
                .build();
    }

    @ResponseBody
    @ExceptionHandler(InvalidStripeWebhookException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleInvalidStripeWebhook(InvalidStripeWebhookException exception) {
        log.warn(exception.getMessage());
        return ErrorDTO.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(exception.getMessage())
                .build();
    }
}
