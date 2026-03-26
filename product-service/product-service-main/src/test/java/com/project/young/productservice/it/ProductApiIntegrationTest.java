package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.command.CreateCategoryCommand;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductCommand;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = ProductServiceMain.class
)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class ProductApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&currentSchema=product";

        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();
        categoryJpaRepository.deleteAll();
        entityManager.flush();

        // 카테고리 시퀀스 초기화 (PostgreSQL 한정)
        try {
            entityManager.createNativeQuery("ALTER SEQUENCE product.categories_id_seq RESTART WITH 1").executeUpdate();
        } catch (Exception e) {
            try {
                entityManager.createNativeQuery("ALTER SEQUENCE categories_id_seq RESTART WITH 1").executeUpdate();
            } catch (Exception ex) {
                System.out.println("Category sequence reset failed: " + ex.getMessage());
            }
        }
    }

    private Long createCategory(String name) throws Exception {
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name(name)
                .parentId(null)
                .build();

        mockMvc.perform(post("/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        return 1L;
    }

    @Nested
    @DisplayName("제품 생성/수정/삭제 API")
    class ProductCrudTests {

        @Test
        @DisplayName("제품 생성 성공")
        @WithMockUser(authorities = "ADMIN")
        void createProduct_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand command = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            // When & Then
            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.message").value(containsString("created successfully")));
        }

        @Test
        @DisplayName("제품 수정 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateProduct_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            String createResponse = mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            UUID productId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

            UpdateProductCommand updateCommand = UpdateProductCommand.builder()
                    .name("와이드핏 데님 수정")
                    .description("수정된 와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("89000"))
                    .brand("브랜드B")
                    .mainImageUrl("https://example.com/updated.jpg")
                    .categoryId(categoryId)
                    .status(ProductStatus.INACTIVE)
                    .build();

            // When & Then
            mockMvc.perform(put("/products/{productId}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님 수정"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.message").value(containsString("updated successfully")));
        }

        @Test
        @DisplayName("제품 삭제(소프트 삭제) 성공")
        @WithMockUser(authorities = "ADMIN")
        void deleteProduct_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            String createResponse = mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            UUID productId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

            // When & Then
            mockMvc.perform(delete("/products/{productId}", productId)
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.message").value(containsString("deleted successfully")));
        }
    }

    @Nested
    @DisplayName("제품 조회 API (일반 사용자)")
    class ProductQueryTests {

        @Test
        @DisplayName("GET /queries/products - 전체 보이는 상품 목록 조회")
        @WithMockUser(authorities = "ADMIN")
        void getAllVisibleProducts_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // When & Then
            mockMvc.perform(get("/queries/products"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.products").isArray())
                    .andExpect(jsonPath("$.products.length()").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.products[0].name").value("와이드핏 데님"));
        }

        @Test
        @DisplayName("GET /queries/products/{productId} - 단일 상품 상세 조회")
        @WithMockUser(authorities = "ADMIN")
        void getProductDetail_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            String createResponse = mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            UUID productId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

            // When & Then
            mockMvc.perform(get("/queries/products/{productId}", productId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("GET /queries/products/categories/{categoryId} - 카테고리별 상품 목록 조회")
        @WithMockUser(authorities = "ADMIN")
        void getProductsByCategory_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // When & Then
            mockMvc.perform(get("/queries/products/categories/{categoryId}", categoryId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.products").isArray())
                    .andExpect(jsonPath("$.products.length()").value(1))
                    .andExpect(jsonPath("$.products[0].categoryId").value(categoryId))
                    .andExpect(jsonPath("$.products[0].name").value("와이드핏 데님"));
        }
    }

    @Nested
    @DisplayName("제품 조회 API (관리자)")
    class AdminProductQueryTests {

        @Test
        @DisplayName("GET /admin/queries/products/{productId} - 어드민 상세 조회 성공")
        @WithMockUser(authorities = "ADMIN")
        void adminGetProductDetail_Success() throws Exception {
            // Given: 카테고리 + 상품 생성
            Long categoryId = createCategory("의류");
            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();
            String createResponse = mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            UUID productId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

            // When & Then
            mockMvc.perform(get("/admin/queries/products/{productId}", productId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.description").value("와이드핏 데님 상세 설명입니다. 20자 이상."))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.conditionType").value("NEW"));
        }

        @Test
        @DisplayName("GET /admin/queries/products - ADMIN 권한으로 검색 성공")
        @WithMockUser(authorities = "ADMIN")
        void adminSearchProducts_Success() throws Exception {
            // Given
            Long categoryId = createCategory("의류");

            CreateProductCommand createCommand = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(categoryId)
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // When & Then
            mockMvc.perform(get("/admin/queries/products")
                            .param("page", "0")
                            .param("size", "20")
                            .param("includeOrphans", "true"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.content[0].name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        @DisplayName("GET /admin/queries/products - ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void adminSearchProducts_WithoutAdmin_Returns403() throws Exception {
            mockMvc.perform(get("/admin/queries/products"))
                    .andExpect(status().isForbidden());
        }
    }
}

