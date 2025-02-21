package com.project.young.productservice;

import com.project.young.common.domain.util.IdentityGenerator;
import com.project.young.common.domain.valueobject.ProductID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostgresUUIDGenerator implements IdentityGenerator<ProductID> {

    private final JdbcTemplate jdbcTemplate;

    public PostgresUUIDGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProductID generateID() {
        UUID uuid = jdbcTemplate.queryForObject("SELECT uuid_generate_v1mc()", UUID.class);
        return new ProductID(uuid);
    }
}
