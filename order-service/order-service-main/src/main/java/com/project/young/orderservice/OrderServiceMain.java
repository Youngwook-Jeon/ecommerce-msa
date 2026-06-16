package com.project.young.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.project.young")
public class OrderServiceMain {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceMain.class, args);
    }
}
