package com.project.young.productservice.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ProductController {

    public ProductController() {
    }

    @GetMapping("products")
    public ResponseEntity<String> getAll() {
        return ResponseEntity.ok("products");
    }
}
