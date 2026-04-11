package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = OptionGroupJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OptionGroupJpaRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private OptionGroupJpaRepository optionGroupJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("TRUNCATE TABLE option_values, option_groups CASCADE").executeUpdate();
        entityManager.flush();
    }

    @Nested
    @DisplayName("CRUD 및 EntityGraph")
    class CrudAndEntityGraphTests {

        @Test
        @DisplayName("옵션 그룹과 값 저장 후 findById로 optionValues를 함께 로드한다")
        void saveAndFindById_LoadsOptionValuesViaEntityGraph() {
            UUID groupId = UUID.randomUUID();
            UUID valueId1 = UUID.randomUUID();
            UUID valueId2 = UUID.randomUUID();

            OptionGroupEntity group = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();

            OptionValueEntity v1 = OptionValueEntity.builder()
                    .id(valueId1)
                    .value("RED")
                    .displayName("빨강")
                    .sortOrder(0)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            OptionValueEntity v2 = OptionValueEntity.builder()
                    .id(valueId2)
                    .value("BLUE")
                    .displayName("파랑")
                    .sortOrder(1)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            group.addOptionValue(v1);
            group.addOptionValue(v2);

            optionGroupJpaRepository.saveAndFlush(group);
            testEntityManager.clear();

            Optional<OptionGroupEntity> found = optionGroupJpaRepository.findAggregateById(groupId);

            assertThat(found).isPresent();
            OptionGroupEntity loaded = found.orElseThrow();
            assertThat(loaded.getName()).isEqualTo("COLOR");
            assertThat(loaded.getOptionValues()).hasSize(2);
            assertThat(loaded.getOptionValues())
                    .extracting(OptionValueEntity::getValue)
                    .containsExactlyInAnyOrder("RED", "BLUE");
        }
    }

    @Nested
    @DisplayName("existsByName")
    class ExistsByNameTests {

        @Test
        @DisplayName("이름으로 존재 여부를 확인한다")
        void existsByName_ReturnsTrueWhenPresent() {
            UUID groupId = UUID.randomUUID();
            OptionGroupEntity group = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("STORAGE")
                    .displayName("저장용량")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();
            optionGroupJpaRepository.saveAndFlush(group);

            assertThat(optionGroupJpaRepository.existsByName("STORAGE")).isTrue();
            assertThat(optionGroupJpaRepository.existsByName("UNKNOWN")).isFalse();
        }
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
