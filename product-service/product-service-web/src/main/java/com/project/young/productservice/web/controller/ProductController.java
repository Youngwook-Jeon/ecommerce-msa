package com.project.young.productservice.web.controller;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("products")
public class ProductController {

//    private final ProductApplicationService productApplicationService;

    public ProductController() {
    }

    @GetMapping
    public ResponseEntity<String> getAll() {
        return ResponseEntity.ok("products");
    }

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody CreateProductCommand createProductCommand) {
        return ResponseEntity.ok("product created");
    }
}
