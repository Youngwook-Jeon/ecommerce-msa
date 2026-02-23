package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.UpdateCategoryCommand;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
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

import static org.hamcrest.Matchers.*;
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
public class CategoryApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
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
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // 모든 카테고리 데이터 삭제
        categoryJpaRepository.deleteAll();
        categoryJpaRepository.flush();

        // 시퀀스 초기화 (PostgreSQL 한정)
        try {
            entityManager.createNativeQuery("ALTER SEQUENCE product.categories_id_seq RESTART WITH 1").executeUpdate();
        } catch (Exception e) {
            try {
                entityManager.createNativeQuery("ALTER SEQUENCE categories_id_seq RESTART WITH 1").executeUpdate();
            } catch (Exception ex) {
                System.out.println("시퀀스 초기화 실패: " + ex.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("카테고리 생성 API")
    class CreateCategoryTests {

        @Test
        @DisplayName("최상위 카테고리 생성 성공")
        @WithMockUser(authorities = "ADMIN")
        void createRootCategory_Success() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자제품"))
                    .andExpect(jsonPath("$.message").value(containsString("created successfully")));
        }

        @Test
        @DisplayName("하위 카테고리 생성 성공")
        @WithMockUser(authorities = "ADMIN")
        void createChildCategory_Success() throws Exception {
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));

            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("스마트폰")
                    .parentId(1L)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.name").value("스마트폰"))
                    .andExpect(jsonPath("$.message").value(containsString("created successfully")));
        }

        @Test
        @DisplayName("중복 카테고리명이면 409 Conflict")
        @WithMockUser(authorities = "ADMIN")
        void create_DuplicateName_Returns409() throws Exception {
            CreateCategoryCommand first = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(first)))
                    .andExpect(status().isCreated());

            CreateCategoryCommand duplicate = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicate)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("name이 blank면 400 Bad Request")
        @WithMockUser(authorities = "ADMIN")
        void create_BlankName_Returns400() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("  ")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("name이 2자 미만이면 400 Bad Request")
        @WithMockUser(authorities = "ADMIN")
        void create_NameTooShort_Returns400() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("A")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void create_WithoutAdmin_Returns403() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("카테고리 수정 API")
    class UpdateCategoryTests {

        @Test
        @DisplayName("이름 변경 성공")
        @WithMockUser(authorities = "ADMIN")
        void update_NameChange_Success() throws Exception {
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));

            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("전자기기")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();
            mockMvc.perform(put("/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자기기"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value(containsString("updated successfully")));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 수정 시 404 Not Found")
        @WithMockUser(authorities = "ADMIN")
        void update_NotFound_Returns404() throws Exception {
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();

            mockMvc.perform(put("/categories/999")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void update_WithoutAdmin_Returns403() throws Exception {
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("전자기기")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();
            mockMvc.perform(put("/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 API")
    class DeleteCategoryTests {

        @Test
        @DisplayName("소프트 삭제 성공")
        @WithMockUser(authorities = "ADMIN")
        void delete_Success() throws Exception {
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));

            mockMvc.perform(delete("/categories/1")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자제품"))
                    .andExpect(jsonPath("$.message").value(containsString("deleted successfully")));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 삭제 시 404 Not Found")
        @WithMockUser(authorities = "ADMIN")
        void delete_NotFound_Returns404() throws Exception {
            mockMvc.perform(delete("/categories/999")
                            .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void delete_WithoutAdmin_Returns403() throws Exception {
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            mockMvc.perform(delete("/categories/1")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("카테고리 계층 조회 API")
    class CategoryHierarchyQueryTests {

        @Test
        @DisplayName("GET /queries/categories/hierarchy - 전체 활성 계층 조회")
        @WithMockUser(authorities = "ADMIN")
        void getPublicHierarchy_Success() throws Exception {
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("스마트폰")
                    .parentId(1L)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.categories.length()").value(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("GET /queries/categories/hierarchy - 빈 계층 조회")
        void getPublicHierarchy_Empty() throws Exception {
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.categories.length()").value(0));
        }

        @Test
        @DisplayName("GET /queries/categories/admin/hierarchy - ADMIN 권한으로 관리자 계층 조회")
        @WithMockUser(authorities = "ADMIN")
        void getAdminHierarchy_WithAdmin_Success() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/queries/categories/admin/hierarchy"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.categories.length()").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.categories[0].name").value("전자제품"))
                    .andExpect(jsonPath("$.categories[0].status").value("ACTIVE"));
        }

        @Test
        @DisplayName("GET /queries/categories/admin/hierarchy - ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void getAdminHierarchy_WithoutAdmin_Returns403() throws Exception {
            mockMvc.perform(get("/queries/categories/admin/hierarchy"))
                    .andExpect(status().isForbidden());
        }
    }
}