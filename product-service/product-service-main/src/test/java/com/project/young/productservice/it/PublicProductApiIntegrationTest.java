package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantsCommand;
import com.project.young.productservice.application.dto.command.CreateCategoryCommand;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateCategoryCommand;
import com.project.young.productservice.application.dto.command.UpdateProductStatusCommand;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PublicProductApiIntegrationTest {

    private static final String PUBLIC_PRODUCTS = "/public/products";
    private static final String PUBLIC_PRODUCT_DETAIL = "/public/products/{productId}";
    private static final String PUBLIC_PRODUCT_FACETS = "/public/products/facets";

    private static final GrantedAuthority[] ADMIN_AUTHORITIES = {
            new SimpleGrantedAuthority("ADMIN")
    };

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
    private OptionGroupJpaRepository optionGroupJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery(
                "TRUNCATE TABLE categories, option_groups, products RESTART IDENTITY CASCADE"
        ).executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("GET /public/products — 요청 검증")
    class RequestValidationTests {

        @Test
        @DisplayName("categoryId 누락 시 400")
        void list_withoutCategoryId_returnsBadRequest() throws Exception {
            mockMvc.perform(get(PUBLIC_PRODUCTS))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("Bad Request"));
        }

        @Test
        @DisplayName("존재하지 않는 categoryId면 404")
        void list_unknownCategory_returnsNotFound() throws Exception {
            mockMvc.perform(get(PUBLIC_PRODUCTS).param("categoryId", "99999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("Category not found")));
        }

        @Test
        @DisplayName("비활성 카테고리면 404")
        void list_inactiveCategory_returnsNotFound() throws Exception {
            Long categoryId = createCategory("의류");

            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("의류")
                    .parentId(null)
                    .status(CategoryStatus.INACTIVE)
                    .build();

            mockMvc.perform(put("/categories/{categoryId}", categoryId)
                            .with(user("admin").authorities(ADMIN_AUTHORITIES))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk());

            mockMvc.perform(get(PUBLIC_PRODUCTS).param("categoryId", String.valueOf(categoryId)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /public/products — 목록 조회")
    class ListProductsTests {

        @Test
        @DisplayName("인증 없이 ACTIVE 상품만 페이지로 조회")
        void list_withoutAuth_returnsActiveProductsOnly() throws Exception {
            Long categoryId = createCategory("의류");
            createDraftProduct("드래프트 데님", "브랜드A", new BigDecimal("10000"), categoryId);
            UUID activeId = createDraftProduct("공개 데님", "브랜드A", new BigDecimal("30000"), categoryId);
            publishProduct(activeId);

            mockMvc.perform(get(PUBLIC_PRODUCTS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("page", "0")
                            .param("size", "24"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(activeId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("공개 데님"))
                    .andExpect(jsonPath("$.content[0].brand").value("브랜드A"))
                    .andExpect(jsonPath("$.content[0].basePrice").value(30000))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(24))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("상품이 없으면 빈 페이지")
        void list_emptyCategory_returnsEmptyPage() throws Exception {
            Long categoryId = createCategory("빈 카테고리");

            mockMvc.perform(get(PUBLIC_PRODUCTS).param("categoryId", String.valueOf(categoryId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }
    }

    @Nested
    @DisplayName("GET /public/products — 필터·정렬")
    class FilterAndSortTests {

        @Test
        @DisplayName("brand·q·가격 범위 필터와 price_asc 정렬")
        void list_withFiltersAndSort() throws Exception {
            Long categoryId = createCategory("필터 카테고리");

            UUID lowBrandA = createDraftProduct("저가 데님", "브랜드A", new BigDecimal("9000"), categoryId);
            UUID highBrandA = createDraftProduct("고가 데님", "브랜드A", new BigDecimal("50000"), categoryId);
            UUID otherBrand = createDraftProduct("코튼 셔츠", "브랜드B", new BigDecimal("20000"), categoryId);

            publishProduct(lowBrandA);
            publishProduct(highBrandA);
            publishProduct(otherBrand);

            mockMvc.perform(get(PUBLIC_PRODUCTS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("brands", "브랜드A")
                            .param("q", "데님")
                            .param("minPrice", "10000")
                            .param("maxPrice", "60000")
                            .param("sort", "price_asc"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(highBrandA.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("고가 데님"));
        }

        @Test
        @DisplayName("페이지네이션")
        void list_paginates() throws Exception {
            Long categoryId = createCategory("페이지 카테고리");

            publishProduct(createDraftProduct("상품 A", "브랜드X", new BigDecimal("11000"), categoryId));
            publishProduct(createDraftProduct("상품 B", "브랜드X", new BigDecimal("12000"), categoryId));
            publishProduct(createDraftProduct("상품 C", "브랜드X", new BigDecimal("13000"), categoryId));

            mockMvc.perform(get(PUBLIC_PRODUCTS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
    }

    @Nested
    @DisplayName("GET /public/products/facets — 퍼싯 조회")
    class FacetTests {

        @Test
        @DisplayName("categoryId 누락 시 400")
        void facets_withoutCategoryId_returnsBadRequest() throws Exception {
            mockMvc.perform(get(PUBLIC_PRODUCT_FACETS))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("Bad Request"));
        }

        @Test
        @DisplayName("존재하지 않는 categoryId면 404")
        void facets_unknownCategory_returnsNotFound() throws Exception {
            mockMvc.perform(get(PUBLIC_PRODUCT_FACETS).param("categoryId", "99999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("Category not found")));
        }

        @Test
        @DisplayName("invalid facet 값이면 400")
        void facets_invalidFacet_returnsBadRequest() throws Exception {
            Long categoryId = createCategory("퍼싯 카테고리");

            mockMvc.perform(get(PUBLIC_PRODUCT_FACETS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("facet", "invalid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("Bad Request"));
        }

        @Test
        @DisplayName("브랜드/가격 퍼싯과 totalMatching을 반환한다")
        void facets_returnsBrandAndPriceBuckets() throws Exception {
            Long categoryId = createCategory("퍼싯 카테고리");

            UUID a = createDraftProduct("저가 데님", "BrandA", new BigDecimal("15"), categoryId);
            UUID b = createDraftProduct("중가 데님", "BrandB", new BigDecimal("75"), categoryId);
            UUID c = createDraftProduct("고가 코튼", "BrandB", new BigDecimal("250"), categoryId);
            publishProduct(a);
            publishProduct(b);
            publishProduct(c);

            mockMvc.perform(get(PUBLIC_PRODUCT_FACETS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("facet", "brand")
                            .param("facet", "price"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoryId").value(categoryId))
                    .andExpect(jsonPath("$.totalMatching").value(3))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].type").value(hasItem("terms")))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].terms[*][?(@.value=='BrandA')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].terms[*][?(@.value=='BrandB')].count").value(hasItem(2)))
                    .andExpect(jsonPath("$.facets[?(@.key=='price')].ranges[*][?(@.id=='under_25')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.facets[?(@.key=='price')].ranges[*][?(@.id=='50_100')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.facets[?(@.key=='price')].ranges[*][?(@.id=='200_plus')].count").value(hasItem(1)));
        }

        @Test
        @DisplayName("brand 필터는 totalMatching에만 적용되고 brand 퍼싯은 disjunctive로 집계된다")
        void facets_brandFilter_keepsDisjunctiveBrandCounts() throws Exception {
            Long categoryId = createCategory("퍼싯 브랜드 카테고리");

            UUID m = createDraftProduct("러닝화", "BrandM", new BigDecimal("160"), categoryId);
            UUID n = createDraftProduct("샌들", "BrandN", new BigDecimal("150"), categoryId);
            publishProduct(m);
            publishProduct(n);

            mockMvc.perform(get(PUBLIC_PRODUCT_FACETS)
                            .param("categoryId", String.valueOf(categoryId))
                            .param("brands", "BrandN")
                            .param("facet", "brand"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMatching").value(1))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].terms[*][?(@.value=='BrandM')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].terms[*][?(@.value=='BrandN')].count").value(hasItem(1)))
                    .andExpect(jsonPath("$.facets[?(@.key=='brand')].terms[*][?(@.value=='BrandN')].selected").value(hasItem(true)));
        }
    }

    @Nested
    @DisplayName("GET /public/products/{productId} — 상세 조회")
    class ProductDetailTests {

        @Test
        @DisplayName("INACTIVE 상품은 200과 상세 본문 반환")
        void detail_inactiveProduct_returnsOk() throws Exception {
            Long categoryId = createCategory("PDP 카테고리");
            UUID productId = createDraftProduct("프리뷰 상품", "브랜드A", new BigDecimal("39000"), categoryId);
            publishProduct(productId);
            updateProductStatus(productId, ProductStatus.INACTIVE);

            mockMvc.perform(get(PUBLIC_PRODUCT_DETAIL, productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.purchasable").value(false))
                    .andExpect(jsonPath("$.listedInCatalog").value(false))
                    .andExpect(jsonPath("$.images").isArray())
                    .andExpect(jsonPath("$.optionGroups").isArray())
                    .andExpect(jsonPath("$.optionGroups[0].optionValues").isArray())
                    .andExpect(jsonPath("$.optionGroups[0].optionValues[0].images").isArray())
                    .andExpect(jsonPath("$.variants").isArray());
        }

        @Test
        @DisplayName("DRAFT/DELETED/없음 상품은 404")
        void detail_draftDeletedAndMissing_returns404() throws Exception {
            Long categoryId = createCategory("PDP 카테고리2");
            UUID draftProductId = createDraftProduct("드래프트 상품", "브랜드D", new BigDecimal("10000"), categoryId);
            UUID deletedProductId = createDraftProduct("삭제 상품", "브랜드X", new BigDecimal("20000"), categoryId);

            updateProductStatus(deletedProductId, ProductStatus.DELETED);

            mockMvc.perform(get(PUBLIC_PRODUCT_DETAIL, draftProductId))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(PUBLIC_PRODUCT_DETAIL, deletedProductId))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(PUBLIC_PRODUCT_DETAIL, UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    private Long createCategory(String name) throws Exception {
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name(name)
                .parentId(null)
                .build();

        String response = mockMvc.perform(post("/categories")
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private UUID createDraftProduct(String name, String brand, BigDecimal basePrice, Long categoryId)
            throws Exception {
        CreateProductCommand command = CreateProductCommand.builder()
                .name(name)
                .description(name + " 상세 설명입니다. 20자 이상 충족.")
                .basePrice(basePrice)
                .brand(brand)
                .mainImageUrl("https://example.com/" + UUID.randomUUID() + ".jpg")
                .categoryId(categoryId)
                .conditionType(ConditionType.NEW)
                .build();

        String response = mockMvc.perform(post("/admin/products")
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void publishProduct(UUID productId) throws Exception {
        OptionFixture optionFixture = createGlobalOptionGroupWithValues(
                UUID.randomUUID().toString().substring(0, 8));

        AddProductOptionGroupCommand addOptionGroupCommand = AddProductOptionGroupCommand.builder()
                .optionGroupId(optionFixture.optionGroupId())
                .stepOrder(1.0d)
                .required(true)
                .optionValues(List.of(
                        AddProductOptionValueCommand.builder()
                                .optionValueId(optionFixture.optionValueIds().getFirst())
                                .priceDelta(BigDecimal.ZERO)
                                .isDefault(true)
                                .isActive(true)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/admin/products/{productId}/option-groups", productId)
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addOptionGroupCommand)))
                .andExpect(status().isCreated());

        ProductEntity aggregate = productJpaRepository.findAggregateById(productId).orElseThrow();
        UUID productOptionValueId = aggregate.getOptionGroups().stream()
                .findFirst()
                .orElseThrow()
                .getOptionValues()
                .stream()
                .findFirst()
                .orElseThrow()
                .getId();

        AddProductVariantsCommand variantsCommand = AddProductVariantsCommand.builder()
                .variants(List.of(
                        AddProductVariantCommand.builder()
                                .stockQuantity(5)
                                .selectedProductOptionValueIds(Set.of(productOptionValueId))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/admin/products/{productId}/variants", productId)
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(variantsCommand)))
                .andExpect(status().isCreated());

        ProductEntity withVariant = productJpaRepository.findAggregateById(productId).orElseThrow();
        withVariant.getVariants().stream().findFirst().orElseThrow().setStatus(ProductStatusEntity.ACTIVE);
        productJpaRepository.save(withVariant);
        entityManager.flush();
        entityManager.clear();

        UpdateProductStatusCommand statusCommand = UpdateProductStatusCommand.builder()
                .status(ProductStatus.ACTIVE)
                .build();

        mockMvc.perform(patch("/admin/products/{productId}/status", productId)
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusCommand)))
                .andExpect(status().isOk());
    }

    private void updateProductStatus(UUID productId, ProductStatus status) throws Exception {
        UpdateProductStatusCommand statusCommand = UpdateProductStatusCommand.builder()
                .status(status)
                .build();

        mockMvc.perform(patch("/admin/products/{productId}/status", productId)
                        .with(user("admin").authorities(ADMIN_AUTHORITIES))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusCommand)))
                .andExpect(status().isOk());
    }

    private OptionFixture createGlobalOptionGroupWithValues(String suffix) {
        OptionGroupEntity group = OptionGroupEntity.builder()
                .name("size-" + suffix)
                .displayName("사이즈-" + suffix)
                .status(OptionStatusEntity.ACTIVE)
                .build();

        OptionValueEntity value = OptionValueEntity.builder()
                .optionGroup(group)
                .value("S-" + suffix)
                .displayName("S")
                .sortOrder(1)
                .status(OptionStatusEntity.ACTIVE)
                .build();

        group.addOptionValue(value);
        OptionGroupEntity saved = optionGroupJpaRepository.save(group);

        return new OptionFixture(
                saved.getId(),
                saved.getOptionValues().stream().map(OptionValueEntity::getId).toList()
        );
    }

    private record OptionFixture(UUID optionGroupId, List<UUID> optionValueIds) {
    }
}
