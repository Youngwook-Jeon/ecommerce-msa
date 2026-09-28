package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;
import com.project.young.paymentservice.application.port.output.CustomerRefundDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRefundDltApplicationService {
    private static final Logger log = LoggerFactory.getLogger(CustomerRefundDltApplicationService.class);
    private final CustomerRefundDltPort queue;

    public CustomerRefundDltApplicationService(CustomerRefundDltPort queue) {
        this.queue = queue;
    }

    @Transactional
    public boolean record(RecordCustomerRefundDltCommand command) {
        boolean recorded = queue.recordIfAbsent(command);
        log.info("Stored customer refund DLT topic={} partition={} offset={} newlyRecorded={}",
                command.dltTopic(), command.dltPartition(), command.dltOffset(), recorded);
        return recorded;
    }
}
