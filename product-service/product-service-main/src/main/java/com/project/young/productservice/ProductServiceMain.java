package com.project.young.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.project.young")
public class ProductServiceMain {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceMain.class, args);
    }
}