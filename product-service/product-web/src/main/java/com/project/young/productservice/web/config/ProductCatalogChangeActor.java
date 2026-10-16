package com.project.young.productservice.web.config;

import com.project.young.productservice.application.port.output.ProductCatalogChangeActorPort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogChangeActor implements ProductCatalogChangeActorPort {
    @Override
    public String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt
                && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return "SYSTEM";
    }
}
