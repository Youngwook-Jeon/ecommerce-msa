package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.UpdateCategoryCommand;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
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
import org.springframework.test.web.servlet.MvcResult;
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
        // 트랜잭션 내에서 실행되므로 별도 트랜잭션 어노테이션 불필요
        try {
            entityManager.createNativeQuery("ALTER SEQUENCE product.categories_id_seq RESTART WITH 1").executeUpdate();
        } catch (Exception e) {
            // 시퀀스가 존재하지 않을 수 있으므로 예외 처리
            // 또는 시퀀스 이름이 다를 수 있음 (categories_id_seq)
            try {
                entityManager.createNativeQuery("ALTER SEQUENCE categories_id_seq RESTART WITH 1").executeUpdate();
            } catch (Exception ex) {
                // 시퀀스 초기화 실패 시 로그만 출력하고 계속 진행
                System.out.println("시퀀스 초기화 실패: " + ex.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("카테고리 생성 API 통합 테스트")
    class CreateCategoryIntegrationTests {

        @Test
        @DisplayName("최상위 카테고리 생성 성공")
        @WithMockUser(authorities = "ADMIN")
        void createRootCategory_Success() throws Exception {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("전자제품"))
                    .andExpect(jsonPath("$.message").value(containsString("created successfully")));
        }

        @Test
        @DisplayName("하위 카테고리 생성 성공")
        @WithMockUser(authorities = "ADMIN")
        void createChildCategory_Success() throws Exception {
            // Given - 부모 카테고리 먼저 생성
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            MvcResult parentResult = mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // 부모 카테고리의 ID를 추출 (시퀀스가 1부터 시작하므로 1L)
            Long parentId = 1L;

            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("노트북")
                    .parentId(parentId)
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("노트북"))
                    .andExpect(jsonPath("$.message").value(containsString("created successfully")));
        }

        @Test
        @DisplayName("중복된 이름으로 카테고리 생성 실패")
        @WithMockUser(authorities = "ADMIN")
        void createCategory_DuplicateName_Fails() throws Exception {
            // Given - 첫 번째 카테고리 생성
            CreateCategoryCommand firstCommand = CreateCategoryCommand.builder()
                    .name("중복카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstCommand)))
                    .andExpect(status().isCreated());

            // 같은 이름으로 두 번째 카테고리 생성 시도
            CreateCategoryCommand duplicateCommand = CreateCategoryCommand.builder()
                    .name("중복카테고리")
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateCommand)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("권한 없이 카테고리 생성 시도 실패")
        void createCategory_WithoutAuth_Fails() throws Exception {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("무권한카테고리")
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 요청으로 카테고리 생성 실패")
        @WithMockUser(authorities = "ADMIN")
        void createCategory_InvalidRequest_Fails() throws Exception {
            // Given - 이름이 없는 잘못된 요청
            CreateCategoryCommand invalidCommand = CreateCategoryCommand.builder()
                    .name("")  // 빈 이름
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidCommand)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("카테고리 수정 API 통합 테스트")
    class UpdateCategoryIntegrationTests {

        @Test
        @DisplayName("카테고리 이름 수정 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_Name_Success() throws Exception {
            // Given - 카테고리 먼저 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("원본카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // 카테고리 ID는 1이라고 가정 (시퀀스 초기화 때문에)
            Long categoryId = 1L;

            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정된카테고리")
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("수정된카테고리"))
                    .andExpect(jsonPath("$.message").value(containsString("updated successfully")));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 수정 시도 실패")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_NotFound_Fails() throws Exception {
            // Given
            Long nonExistentId = 999L;
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정카테고리")
                    .parentId(null)
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", nonExistentId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 API 통합 테스트")
    class DeleteCategoryIntegrationTests {

        @Test
        @DisplayName("카테고리 삭제 성공")
        @WithMockUser(authorities = "ADMIN")
        void deleteCategory_Success() throws Exception {
            // Given - 카테고리 먼저 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("삭제될카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // 카테고리 ID는 1이라고 가정
            Long categoryId = 1L;

            // When & Then
            mockMvc.perform(delete("/categories/{id}", categoryId)
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(categoryId))
                    .andExpect(jsonPath("$.message").value(containsString("marked as deleted successfully")));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 삭제 시도 실패")
        @WithMockUser(authorities = "ADMIN")
        void deleteCategory_NotFound_Fails() throws Exception {
            // Given
            Long nonExistentId = 999L;

            // When & Then
            mockMvc.perform(delete("/categories/{id}", nonExistentId)
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }
    }

    @Nested
    @DisplayName("카테고리 계층 조회 API 통합 테스트")
    class CategoryHierarchyIntegrationTests {

        @Test
        @DisplayName("카테고리 계층 조회 성공")
        @WithMockUser(authorities = "ADMIN")
        void getCategoryHierarchy_Success() throws Exception {
            // Given - 계층 구조 카테고리 생성
            // 부모 카테고리 생성
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            // 자식 카테고리 생성 (부모 ID는 1이라고 가정)
            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("노트북")
                    .parentId(1L)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andExpect(status().isCreated());

            // When & Then
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1))) // 루트 카테고리 1개
                    .andExpect(jsonPath("$[0].name").value("전자제품"))
                    .andExpect(jsonPath("$[0].parentId").isEmpty())
                    .andExpect(jsonPath("$[0].children").isArray())
                    .andExpect(jsonPath("$[0].children", hasSize(1))) // 자식 카테고리 1개
                    .andExpect(jsonPath("$[0].children[0].name").value("노트북"));
        }

        @Test
        @DisplayName("빈 계층 조회")
        @WithMockUser(authorities = "USER")
        void getCategoryHierarchy_Empty_Success() throws Exception {
            // When & Then - 카테고리가 없는 상태에서 조회
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0))); // 빈 배열
        }
    }

    @Nested
    @DisplayName("완전한 CRUD 시나리오 통합 테스트")
    class FullCrudScenarioTests {

        @Test
        @DisplayName("카테고리 생성 -> 조회 -> 수정 -> 삭제 전체 시나리오")
        @WithMockUser(authorities = "ADMIN")
        void fullCrudScenario_Success() throws Exception {
            // 1. 카테고리 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("시나리오테스트카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("시나리오테스트카테고리"));

            // 2. 계층 조회로 생성된 카테고리 확인
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("시나리오테스트카테고리"));

            // 3. 카테고리 수정
            Long categoryId = 1L; // 첫 번째 카테고리 ID
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정된시나리오카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("수정된시나리오카테고리"));

            // 4. 수정된 내용 확인
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("수정된시나리오카테고리"));

            // 5. 카테고리 삭제
            mockMvc.perform(delete("/categories/{id}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(containsString("marked as deleted")));

            // 6. 삭제 후 계층 조회 (삭제된 카테고리는 보이지 않아야 함)
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0))); // 활성 카테고리가 없어야 함
        }
    }
}