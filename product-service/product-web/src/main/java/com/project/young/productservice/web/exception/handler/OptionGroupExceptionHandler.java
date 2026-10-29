package com.project.young.productservice.web.exception.handler;

import com.project.young.common.application.web.ErrorDTO;
import com.project.young.productservice.application.exception.OptionGroupVersionConflictException;
import com.project.young.productservice.web.controller.OptionGroupController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Keeps option dictionary version conflicts within the option API error contract. */
@Slf4j
@ControllerAdvice(assignableTypes = OptionGroupController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OptionGroupExceptionHandler {
    @ResponseBody
    @ExceptionHandler({OptionGroupVersionConflictException.class, OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleVersionConflict(Exception exception) {
        log.info("Option group edit rejected: code=OPTION_GROUP_VERSION_CONFLICT, exceptionType={}",
                exception.getClass().getSimpleName());
        return ErrorDTO.builder().code("OPTION_GROUP_VERSION_CONFLICT")
                .message("The option group has changed. Reload the latest option group before editing.").build();
    }
}
