package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.CustomerRefundDltEntity;
import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CustomerRefundDltJpaRepository extends JpaRepository<CustomerRefundDltEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO customer_refund_dlts (dlt_topic, dlt_partition, dlt_offset, message_key, payload,
                source_topic, source_partition, source_offset, exception_class, exception_message)
            VALUES (:#{#c.dltTopic()}, :#{#c.dltPartition()}, :#{#c.dltOffset()}, :#{#c.messageKey()},
                :#{#c.payload()}, :#{#c.sourceTopic()}, :#{#c.sourcePartition()}, :#{#c.sourceOffset()},
                :#{#c.exceptionClass()}, :#{#c.exceptionMessage()})
            ON CONFLICT (dlt_topic, dlt_partition, dlt_offset) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("c") RecordCustomerRefundDltCommand command);
}
