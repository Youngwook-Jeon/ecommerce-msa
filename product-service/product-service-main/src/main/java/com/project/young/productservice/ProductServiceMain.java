package com.project.young.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.project.young")
@EnableScheduling
public class ProductServiceMain {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceMain.class, args);
    }
}