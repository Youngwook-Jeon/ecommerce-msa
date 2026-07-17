package com.project.young.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.project.young")
public class PaymentServiceMain {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceMain.class, args);
    }
}
