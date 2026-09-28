package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.command.RecordCustomerRefundDltCommand;
import com.project.young.orderservice.application.port.output.CustomerRefundDltPort;
import com.project.young.orderservice.dataaccess.repository.CustomerRefundDltJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerRefundDltAdapter implements CustomerRefundDltPort {

    private final CustomerRefundDltJpaRepository repository;

    public CustomerRefundDltAdapter(CustomerRefundDltJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean recordIfAbsent(RecordCustomerRefundDltCommand command) {
        return repository.insertIfAbsent(command) == 1;
    }
}
