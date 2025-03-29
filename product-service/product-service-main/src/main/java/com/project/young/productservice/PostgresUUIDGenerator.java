package com.project.young.productservice;

import com.project.young.common.domain.util.IdentityGenerator;
import com.project.young.common.domain.valueobject.ProductId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostgresUUIDGenerator implements IdentityGenerator<ProductId> {

    private final JdbcTemplate jdbcTemplate;

    public PostgresUUIDGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProductId generateID() {
        UUID uuid = jdbcTemplate.queryForObject("SELECT uuid_generate_v1mc()", UUID.class);
        return new ProductId(uuid);
    }
}
