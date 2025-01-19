package com.project.young.productservice.web.controller;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.ports.input.service.ProductApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("products")
public class ProductController {

    private final ProductApplicationService productApplicationService;

    public ProductController(ProductApplicationService productApplicationService) {
        this.productApplicationService = productApplicationService;
    }

    @GetMapping
    public ResponseEntity<String> getAll() {
        return ResponseEntity.ok("products");
    }

    @GetMapping(path = "/auth-test")
    @PreAuthorize("permitAll()")
    public UserDto authTest(Authentication auth) {
        if(auth instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getName();
            List<String> roles = jwt.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
            Instant exp = jwt.getToken().getExpiresAt();
            return new UserDto(username, roles, exp.getEpochSecond());
        }

        return UserDto.ANONYMOUS;
    }

    @GetMapping(path = "/auth-admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ProductResponse> adminTest() {
        return ResponseEntity.ok(new ProductResponse("Admin user"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> create(@Valid @RequestBody CreateProductCommand createProductCommand) {
        return ResponseEntity.ok("product created");
    }

    static record UserDto(@NotNull String username, @NotNull List<String> roles, @NotNull Long exp) {
        static final UserDto ANONYMOUS = new UserDto("", List.of(), Long.MAX_VALUE);
    }

    record ProductResponse(String message) {}
}
