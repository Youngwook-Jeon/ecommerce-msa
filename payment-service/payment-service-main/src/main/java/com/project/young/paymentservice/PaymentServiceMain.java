package com.project.young.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.project.young")
@EnableScheduling
public class PaymentServiceMain {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceMain.class, args);
    }
}
