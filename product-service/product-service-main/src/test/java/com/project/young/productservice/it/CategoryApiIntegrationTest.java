package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.UpdateCategoryCommand;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;

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

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            // 하위 카테고리 생성
            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("노트북")
                    .parentId(1L) // 부모 카테고리 ID
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

        @Test
        @DisplayName("존재하지 않는 부모 카테고리로 생성 실패")
        @WithMockUser(authorities = "ADMIN")
        void createCategory_NonExistentParent_Fails() throws Exception {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("자식카테고리")
                    .parentId(999L) // 존재하지 않는 부모 ID
                    .build();

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }
    }

    @Nested
    @DisplayName("카테고리 수정 API 통합 테스트")
    class UpdateCategoryIntegrationTests {

        @Test
        @DisplayName("카테고리 이름만 수정 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_NameOnly_Success() throws Exception {
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

            Long categoryId = 1L;

            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정된카테고리")
                    .build(); // parentId와 status는 null로 두어 변경하지 않음

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
        @DisplayName("카테고리 부모 변경 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_ParentChange_Success() throws Exception {
            // Given - 부모 카테고리 생성
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("부모카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            // 자식 카테고리 생성 (최상위로)
            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("자식카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andExpect(status().isCreated());

            Long childId = 2L;
            Long parentId = 1L;

            // 자식 카테고리의 부모를 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("자식카테고리")
                    .parentId(parentId) // 부모 설정
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", childId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("자식카테고리"))
                    .andExpect(jsonPath("$.message").value(containsString("updated successfully")));
        }

        @Test
        @DisplayName("카테고리 상태 변경 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_StatusChange_Success() throws Exception {
            // Given - 카테고리 생성 (기본 상태: ACTIVE)
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("상태변경카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            Long categoryId = 1L;

            // 상태를 INACTIVE로 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("상태변경카테고리")
                    .status("INACTIVE")
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("상태변경카테고리"))
                    .andExpect(jsonPath("$.message").value(containsString("updated successfully")));
        }

        @Test
        @DisplayName("모든 필드 동시 수정 성공")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_AllFields_Success() throws Exception {
            // Given - 부모 카테고리 생성
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("새부모카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            // 수정할 카테고리 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("원본카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            Long categoryId = 2L;
            Long parentId = 1L;

            // 모든 필드 동시 수정
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("완전수정된카테고리")
                    .parentId(parentId)
                    .status("INACTIVE")
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("완전수정된카테고리"))
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

        @Test
        @DisplayName("중복된 이름으로 수정 시도 실패")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_DuplicateName_Fails() throws Exception {
            // Given - 첫 번째 카테고리 생성
            CreateCategoryCommand firstCommand = CreateCategoryCommand.builder()
                    .name("기존카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstCommand)))
                    .andExpect(status().isCreated());

            // 두 번째 카테고리 생성
            CreateCategoryCommand secondCommand = CreateCategoryCommand.builder()
                    .name("수정될카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondCommand)))
                    .andExpect(status().isCreated());

            Long secondCategoryId = 2L;

            // 두 번째 카테고리를 첫 번째 카테고리와 같은 이름으로 수정 시도
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("기존카테고리") // 중복된 이름
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", secondCategoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("잘못된 상태값으로 수정 시도 실패")
        @WithMockUser(authorities = "ADMIN")
        void updateCategory_InvalidStatus_Fails() throws Exception {
            // Given - 카테고리 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("테스트카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            Long categoryId = 1L;

            // 잘못된 상태값으로 수정 시도
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("테스트카테고리")
                    .status("INVALID_STATUS")
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("권한 없이 카테고리 수정 시도 실패")
        @WithMockUser(authorities = "CUSTOMER")
        void updateCategory_WithoutAuth_Fails() throws Exception {
            // Given
            Long categoryId = 1L;
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정카테고리")
                    .build();

            // When & Then
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 API 통합 테스트")
    class DeleteCategoryIntegrationTests {

        @Test
        @DisplayName("단일 카테고리 삭제 성공")
        @WithMockUser(authorities = "ADMIN")
        void deleteCategory_Single_Success() throws Exception {
            // Given - 카테고리 생성
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("삭제될카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

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
        @DisplayName("하위 카테고리가 있는 카테고리 삭제 성공 (계층 삭제)")
        @WithMockUser(authorities = "ADMIN")
        void deleteCategory_WithChildren_Success() throws Exception {
            // Given - 부모 카테고리 생성
            CreateCategoryCommand parentCommand = CreateCategoryCommand.builder()
                    .name("부모카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parentCommand)))
                    .andExpect(status().isCreated());

            // 자식 카테고리 생성
            CreateCategoryCommand childCommand = CreateCategoryCommand.builder()
                    .name("자식카테고리")
                    .parentId(1L)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(childCommand)))
                    .andExpect(status().isCreated());

            Long parentId = 1L;

            // When & Then - 부모 카테고리 삭제 (자식도 함께 삭제됨)
            mockMvc.perform(delete("/categories/{id}", parentId)
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(parentId))
                    .andExpect(jsonPath("$.message").value(containsString("marked as deleted successfully")));

            // 삭제 후 계층 조회로 확인
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0))); // 모든 카테고리가 삭제됨
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

        @Test
        @DisplayName("권한 없이 카테고리 삭제 시도 실패")
        @WithMockUser(authorities = "CUSTOMER")
        void deleteCategory_WithoutAuth_Fails() throws Exception {
            // Given
            Long categoryId = 1L;

            // When & Then
            mockMvc.perform(delete("/categories/{id}", categoryId)
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("카테고리 계층 조회 API 통합 테스트")
    class CategoryHierarchyIntegrationTests {

        @Test
        @DisplayName("복잡한 카테고리 계층 조회 성공")
        @WithMockUser(authorities = "ADMIN")
        void getCategoryHierarchy_Complex_Success() throws Exception {
            // Given - 복잡한 계층 구조 생성
            // 루트 카테고리들 생성
            CreateCategoryCommand electronics = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            CreateCategoryCommand clothing = CreateCategoryCommand.builder()
                    .name("의류")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(electronics)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(clothing)))
                    .andExpect(status().isCreated());

            // 전자제품 하위 카테고리들
            CreateCategoryCommand laptop = CreateCategoryCommand.builder()
                    .name("노트북")
                    .parentId(1L)
                    .build();

            CreateCategoryCommand smartphone = CreateCategoryCommand.builder()
                    .name("스마트폰")
                    .parentId(1L)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(laptop)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(smartphone)))
                    .andExpect(status().isCreated());

            // When & Then
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(2))) // 루트 카테고리 2개
                    .andExpect(jsonPath("$[?(@.name == '전자제품')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '의류')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '전자제품')].children[*]", hasSize(2))) // 전자제품 하위 2개
                    .andExpect(jsonPath("$[?(@.name == '의류')].children[*]", hasSize(0))) // 의류 하위 0개
                    .andExpect(jsonPath("$[?(@.name == '전자제품')].children[?(@.name == '노트북')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '전자제품')].children[?(@.name == '스마트폰')]").exists());
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

        @Test
        @DisplayName("INACTIVE 상태 카테고리 포함 계층 조회")
        @WithMockUser(authorities = "ADMIN")
        void getCategoryHierarchy_WithInactive_Success() throws Exception {
            // Given - 카테고리 생성 후 상태 변경
            CreateCategoryCommand createCommand = CreateCategoryCommand.builder()
                    .name("비활성화될카테고리")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createCommand)))
                    .andExpect(status().isCreated());

            // 상태를 INACTIVE로 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("비활성화될카테고리")
                    .status("INACTIVE")
                    .build();

            mockMvc.perform(put("/categories/{id}", 1L)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk());

            // When & Then - 관리자 계층 조회 (INACTIVE 상태도 포함되어야 함)
            mockMvc.perform(get("/queries/categories/admin/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1))) // INACTIVE 상태도 조회됨
                    .andExpect(jsonPath("$[?(@.name == '비활성화될카테고리')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '비활성화될카테고리')].status").value("INACTIVE"));
        }
    }

    @Nested
    @DisplayName("카테고리 상태별 조회 API 통합 테스트")
    class CategoryStatusBasedQueryIntegrationTests {

        @Test
        @DisplayName("일반 사용자 - ACTIVE 카테고리만 조회 성공")
        void getAllActiveCategoryHierarchy_OnlyActiveCategories_Success() throws Exception {
            // Given: 다양한 상태의 카테고리들 생성
            CategoryEntity rootActive = createAndSaveCategory("활성루트", "ACTIVE", null);
            CategoryEntity rootInactive = createAndSaveCategory("비활성루트", "INACTIVE", null);
            CategoryEntity rootDeleted = createAndSaveCategory("삭제된루트", "DELETED", null);

            CategoryEntity childActive = createAndSaveCategory("활성자식", "ACTIVE", rootActive);
            CategoryEntity childInactive = createAndSaveCategory("비활성자식", "INACTIVE", rootActive);
            CategoryEntity childDeleted = createAndSaveCategory("삭제된자식", "DELETED", rootActive);

            entityManager.flush();
            entityManager.clear();

            // When & Then: 일반 사용자 조회 시 ACTIVE 카테고리만 반환
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1))) // ACTIVE 루트 카테고리만
                    .andExpect(jsonPath("$[?(@.name == '활성루트')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children", hasSize(1))) // ACTIVE 자식만
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children[?(@.name == '활성자식')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children[?(@.name == '활성자식')].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[?(@.name == '비활성루트')]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.name == '삭제된루트')]").doesNotExist());
        }

        @Test
        @DisplayName("관리자 - 모든 상태 카테고리 조회 성공")
        @WithMockUser(authorities = "ADMIN")
        void getAdminCategoryHierarchy_AllStatusCategories_Success() throws Exception {
            // Given: 다양한 상태의 카테고리들 생성
            CategoryEntity rootActive = createAndSaveCategory("활성루트", "ACTIVE", null);
            CategoryEntity rootInactive = createAndSaveCategory("비활성루트", "INACTIVE", null);
            CategoryEntity rootDeleted = createAndSaveCategory("삭제된루트", "DELETED", null);

            CategoryEntity childActive = createAndSaveCategory("활성자식", "ACTIVE", rootActive);
            CategoryEntity childInactive = createAndSaveCategory("비활성자식", "INACTIVE", rootActive);

            entityManager.flush();
            entityManager.clear();

            // When & Then: 관리자 조회 시 모든 상태 카테고리 반환
            mockMvc.perform(get("/queries/categories/admin/hierarchy")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3))) // 모든 루트 카테고리
                    .andExpect(jsonPath("$[?(@.name == '활성루트')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '비활성루트')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '삭제된루트')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[?(@.name == '비활성루트')].status").value("INACTIVE"))
                    .andExpect(jsonPath("$[?(@.name == '삭제된루트')].status").value("DELETED"))
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children[*]", hasSize(2))) // 활성+비활성 자식
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children[?(@.name == '활성자식')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '활성루트')].children[?(@.name == '비활성자식')]").exists());
        }

        @Test
        @DisplayName("권한 없는 사용자가 관리자 API 접근 시 401 오류")
        void getAdminCategoryHierarchy_WithoutAdminAuth_Returns401() throws Exception {
            mockMvc.perform(get("/queries/categories/admin/hierarchy")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("일반 사용자 권한으로 관리자 API 접근 시 403 오류")
        @WithMockUser(authorities = "CUSTOMER")
        void getAdminCategoryHierarchy_WithCustomerAuth_Returns403() throws Exception {
            mockMvc.perform(get("/queries/categories/admin/hierarchy")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ACTIVE → INACTIVE 상태 변경 후 일반 사용자 조회에서 제외됨")
        @WithMockUser(authorities = "ADMIN")
        void updateCategoryToInactive_ExcludedFromPublicHierarchy() throws Exception {
            // Given: ACTIVE 카테고리 생성
            CategoryEntity activeCategory = createAndSaveCategory("테스트카테고리", "ACTIVE", null);
            Long categoryId = activeCategory.getId();

            entityManager.flush();
            entityManager.clear();

            // 처음엔 일반 사용자 조회에서 보임
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '테스트카테고리')]").exists());

            // When: INACTIVE로 상태 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("테스트카테고리")
                    .parentId(null)
                    .status("INACTIVE")
                    .build();

            mockMvc.perform(put("/categories/{categoryId}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk());

            // Then: 일반 사용자 조회에서 제외됨
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '테스트카테고리')]").doesNotExist());

            // 하지만 관리자 조회에서는 여전히 보임
            mockMvc.perform(get("/queries/categories/admin/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '테스트카테고리' && @.status == 'INACTIVE')]").exists());
        }

        @Test
        @DisplayName("INACTIVE → ACTIVE 상태 변경 후 일반 사용자 조회에 포함됨")
        @WithMockUser(authorities = "ADMIN")
        void updateCategoryToActive_IncludedInPublicHierarchy() throws Exception {
            // Given: INACTIVE 카테고리 생성
            CategoryEntity inactiveCategory = createAndSaveCategory("비활성카테고리", "INACTIVE", null);
            Long categoryId = inactiveCategory.getId();

            entityManager.flush();
            entityManager.clear();

            // 처음엔 일반 사용자 조회에서 안 보임
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '비활성카테고리')]").doesNotExist());

            // When: ACTIVE로 상태 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("비활성카테고리")
                    .parentId(null)
                    .status("ACTIVE")
                    .build();

            mockMvc.perform(put("/categories/{categoryId}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk());

            // Then: 일반 사용자 조회에 포함됨
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '비활성카테고리' && @.status == 'ACTIVE')]").exists());
        }

        @Test
        @DisplayName("깊은 계층에서 중간 카테고리가 INACTIVE일 때 하위 트리 제외")
        @WithMockUser(authorities = "ADMIN")
        void deepHierarchy_MiddleInactive_SubtreeExcluded() throws Exception {
            // Given: 깊은 계층 구조 생성 (ACTIVE)
            CategoryEntity root = createAndSaveCategory("루트", "ACTIVE", null);
            CategoryEntity level1 = createAndSaveCategory("레벨1", "ACTIVE", root);
            CategoryEntity level2 = createAndSaveCategory("레벨2", "ACTIVE", level1);
            CategoryEntity level3 = createAndSaveCategory("레벨3", "ACTIVE", level2);

            entityManager.flush();
            entityManager.clear();

            // When: 중간 레벨을 INACTIVE로 변경
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("레벨1")
                    .parentId(root.getId())
                    .status("INACTIVE")
                    .build();

            mockMvc.perform(put("/categories/{categoryId}", level1.getId())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk());

            // Then: 일반 사용자는 루트만 보고, 하위 트리는 보이지 않음
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[?(@.name == '루트')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '루트')].children[*]", hasSize(0))); // 레벨1이 INACTIVE이므로 하위 트리 제외
        }

        @Test
        @DisplayName("형제 카테고리들 중 일부만 ACTIVE일 때 올바른 필터링")
        void siblingCategories_MixedStatus_CorrectFiltering() throws Exception {
            // Given: 같은 부모 하에 다양한 상태의 형제 카테고리들
            CategoryEntity parent = createAndSaveCategory("부모", "ACTIVE", null);
            CategoryEntity child1 = createAndSaveCategory("자식1", "ACTIVE", parent);
            CategoryEntity child2 = createAndSaveCategory("자식2", "INACTIVE", parent);
            CategoryEntity child3 = createAndSaveCategory("자식3", "ACTIVE", parent);

            entityManager.flush();
            entityManager.clear();

            // When & Then: 일반 사용자는 ACTIVE 자식들만 봄
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '부모')].children[*]", hasSize(2)))
                    .andExpect(jsonPath("$[?(@.name == '부모')].children[?(@.name == '자식1' && @.status == 'ACTIVE')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '부모')].children[?(@.name == '자식3' && @.status == 'ACTIVE')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '부모')].children[?(@.name == '자식2')]").doesNotExist());
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
                    .andExpect(jsonPath("$[?(@.name == '시나리오테스트카테고리')]").exists());

            // 3. 카테고리 수정 (이름과 상태 동시 변경)
            Long categoryId = 1L;
            UpdateCategoryCommand updateCommand = UpdateCategoryCommand.builder()
                    .name("수정된시나리오카테고리")
                    .status("INACTIVE")
                    .build();

            mockMvc.perform(put("/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateCommand)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("수정된시나리오카테고리"));

            // 4. 수정된 내용 확인 (INACTIVE 상태는 일반 사용자 조회에서 제외됨)
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0))); // INACTIVE 상태이므로 일반 조회에서 제외

            // 관리자 조회에서는 보임
            mockMvc.perform(get("/queries/categories/admin/hierarchy")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == '수정된시나리오카테고리')]").exists())
                    .andExpect(jsonPath("$[?(@.name == '수정된시나리오카테고리')].status").value("INACTIVE"));

            // 5. 카테고리 삭제
            mockMvc.perform(delete("/categories/{id}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(containsString("marked as deleted")));
        }

        @Test
        @DisplayName("계층 구조 변경 시나리오")
        @WithMockUser(authorities = "ADMIN")
        void hierarchyChangeScenario_Success() throws Exception {
            // 1. 부모 카테고리 2개 생성
            CreateCategoryCommand parent1 = CreateCategoryCommand.builder()
                    .name("부모1")
                    .parentId(null)
                    .build();

            CreateCategoryCommand parent2 = CreateCategoryCommand.builder()
                    .name("부모2")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parent1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(parent2)))
                    .andExpect(status().isCreated());

            // 2. 자식 카테고리를 부모1 하위에 생성
            CreateCategoryCommand child = CreateCategoryCommand.builder()
                    .name("자식")
                    .parentId(1L) // 부모1 하위
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(child)))
                    .andExpect(status().isCreated());

            // 3. 계층 구조 확인 - 이름으로 카테고리를 찾아서 검증
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2))) // 루트 카테고리 2개
                    .andExpect(jsonPath("$[?(@.name == '부모1')].children[*]", hasSize(1))) // 부모1의 자식들 크기
                    .andExpect(jsonPath("$[?(@.name == '부모2')].children[*]", hasSize(0))) // 부모2의 자식들 크기
                    .andExpect(jsonPath("$[?(@.name == '부모1')].children[0].name").value("자식")); // 부모1의 첫 번째 자식 이름


            // 4. 자식의 부모를 부모2로 변경
            UpdateCategoryCommand moveChild = UpdateCategoryCommand.builder()
                    .name("자식")
                    .parentId(2L) // 부모2 하위로 이동
                    .build();

            mockMvc.perform(put("/categories/{id}", 3L) // 자식 카테고리 ID
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(moveChild)))
                    .andExpect(status().isOk());

            // 5. 변경된 계층 구조 확인 - 이름으로 카테고리를 찾아서 검증
            mockMvc.perform(get("/queries/categories/hierarchy")
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2))) // 루트 카테고리 2개
                    .andExpect(jsonPath("$[?(@.name == '부모1')].children[*]", hasSize(0))) // 부모1에 자식 0개
                    .andExpect(jsonPath("$[?(@.name == '부모2')].children[*]", hasSize(1))) // 부모2에 자식 1개
                    .andExpect(jsonPath("$[?(@.name == '부모2')].children[0].name").value("자식")); // 부모2의 자식 이름 확인
        }
    }

    private CategoryEntity createAndSaveCategory(String name, String status, CategoryEntity parent) {
        CategoryEntity category = CategoryEntity.builder()
                .name(name)
                .status(status)
                .parent(parent)
                .children(new ArrayList<>())
                .build();

        categoryJpaRepository.save(category);

        if (parent != null) {
            parent.addChild(category);
            categoryJpaRepository.save(parent);
        }

        return category;
    }
}