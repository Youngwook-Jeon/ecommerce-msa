package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.command.RecordCustomerRefundDltCommand;

public interface CustomerRefundDltPort {

    boolean recordIfAbsent(RecordCustomerRefundDltCommand command);
}
