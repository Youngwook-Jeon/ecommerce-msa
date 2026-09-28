package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;

public interface CustomerRefundDltPort {

    boolean recordIfAbsent(RecordCustomerRefundDltCommand command);
}
