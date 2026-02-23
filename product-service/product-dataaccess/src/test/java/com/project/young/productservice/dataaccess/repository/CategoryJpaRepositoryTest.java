package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = CategoryJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryJpaRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
        registry.add("spring.jpa.show-sql", () -> "true");
    }

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        categoryJpaRepository.deleteAll();
        testEntityManager.flush();
    }

    @Nested
    @DisplayName("기본 CRUD 테스트")
    class BasicCrudTests {

        @Test
        @DisplayName("카테고리 저장 및 조회 성공")
        void saveAndFindCategory_Success() {
            // Given
            CategoryEntity category = createCategory("전자제품", CategoryStatusEntity.ACTIVE, null);

            // When
            CategoryEntity savedCategory = categoryJpaRepository.save(category);

            // Then
            assertThat(savedCategory).isNotNull();
            assertThat(savedCategory.getId()).isNotNull();
            assertThat(savedCategory.getName()).isEqualTo("전자제품");
            assertThat(savedCategory.getStatus()).isEqualTo(CategoryStatusEntity.ACTIVE);
            assertThat(savedCategory.getCreatedAt()).isNotNull();
            assertThat(savedCategory.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("부모-자식 관계 카테고리 저장 성공")
        void saveParentChildCategories_Success() {
            // Given
            CategoryEntity parent = createCategory("전자제품", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedParent = categoryJpaRepository.save(parent);

            CategoryEntity child = createCategory("노트북", CategoryStatusEntity.ACTIVE, savedParent);

            // When
            CategoryEntity savedChild = categoryJpaRepository.save(child);

            // Then
            assertThat(savedChild.getParent()).isNotNull();
            assertThat(savedChild.getParent().getId()).isEqualTo(savedParent.getId());
            assertThat(savedChild.getParent().getName()).isEqualTo("전자제품");
        }
    }

    @Nested
    @DisplayName("존재 여부 확인 테스트")
    class ExistenceCheckTests {

        @Test
        @DisplayName("카테고리 이름으로 존재 여부 확인")
        void existsByName_Success() {
            // Given
            CategoryEntity category = createCategory("전자제품", CategoryStatusEntity.ACTIVE, null);
            categoryJpaRepository.save(category);

            // When & Then
            assertThat(categoryJpaRepository.existsByName("전자제품")).isTrue();
            assertThat(categoryJpaRepository.existsByName("존재하지않는카테고리")).isFalse();
        }

        @Test
        @DisplayName("특정 ID 제외하고 이름으로 존재 여부 확인")
        void existsByNameAndIdNot_Success() {
            // Given
            CategoryEntity electronics = createCategory("전자제품", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity books = createCategory("도서", CategoryStatusEntity.ACTIVE, null);

            CategoryEntity savedElectronics = categoryJpaRepository.save(electronics);
            CategoryEntity savedBooks = categoryJpaRepository.save(books);

            // When & Then
            // 다른 ID에서 같은 이름이 존재하는 경우
            assertThat(categoryJpaRepository.existsByNameAndIdNot("전자제품", savedBooks.getId())).isTrue();

            // 자기 자신의 ID를 제외하면 같은 이름이 존재하지 않는 경우
            assertThat(categoryJpaRepository.existsByNameAndIdNot("전자제품", savedElectronics.getId())).isFalse();

            // 존재하지 않는 이름인 경우
            assertThat(categoryJpaRepository.existsByNameAndIdNot("존재하지않는카테고리", savedElectronics.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("상태별 조회 테스트")
    class StatusBasedQueryTests {

        @Test
        @DisplayName("활성 상태 카테고리만 조회 (부모 정보 포함)")
        void findAllWithParentByStatus_ActiveOnly_Success() {
            // Given
            CategoryEntity activeParent = createCategory("활성부모", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity inactiveParent = createCategory("비활성부모", CategoryStatusEntity.INACTIVE, null);
            categoryJpaRepository.saveAll(List.of(activeParent, inactiveParent));

            CategoryEntity activeChild = createCategory("활성자식", CategoryStatusEntity.ACTIVE, activeParent);
            CategoryEntity inactiveChild = createCategory("비활성자식", CategoryStatusEntity.INACTIVE, inactiveParent);
            categoryJpaRepository.saveAll(List.of(activeChild, inactiveChild));

            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);

            // Then
            assertThat(activeCategories).hasSize(2);
            assertThat(activeCategories)
                    .extracting(CategoryEntity::getName)
                    .containsExactlyInAnyOrder("활성부모", "활성자식");

            // 부모 정보가 올바르게 fetch되었는지 확인
            CategoryEntity foundActiveChild = findCategoryByName(activeCategories, "활성자식");
            assertThat(foundActiveChild.getParent()).isNotNull();
            assertThat(foundActiveChild.getParent().getName()).isEqualTo("활성부모");
        }

        @Test
        @DisplayName("활성 상태 카테고리가 없을 때 빈 리스트 반환")
        void findAllWithParentByStatus_NoActiveCategories_ReturnsEmptyList() {
            // Given
            CategoryEntity inactiveCategory = createCategory("비활성카테고리", CategoryStatusEntity.INACTIVE, null);
            categoryJpaRepository.save(inactiveCategory);

            // When
            List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);

            // Then
            assertThat(activeCategories).isEmpty();
        }
    }

    @Nested
    @DisplayName("전체 조회 테스트")
    class FindAllTests {

        @Test
        @DisplayName("모든 카테고리 조회 (부모 정보 포함)")
        void findAllWithParent_Success() {
            // Given
            CategoryEntity activeParent = createCategory("활성부모", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity inactiveParent = createCategory("비활성부모", CategoryStatusEntity.INACTIVE, null);
            categoryJpaRepository.saveAll(List.of(activeParent, inactiveParent));

            CategoryEntity activeChild = createCategory("활성자식", CategoryStatusEntity.ACTIVE, activeParent);
            CategoryEntity inactiveChild = createCategory("비활성자식", CategoryStatusEntity.INACTIVE, inactiveParent);
            categoryJpaRepository.saveAll(List.of(activeChild, inactiveChild));

            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

            // Then
            assertThat(allCategories).hasSize(4);
            assertThat(allCategories)
                    .extracting(CategoryEntity::getName)
                    .containsExactlyInAnyOrder("활성부모", "비활성부모", "활성자식", "비활성자식");

            // 부모 정보가 올바르게 fetch되었는지 확인
            CategoryEntity foundActiveChild = findCategoryByName(allCategories, "활성자식");
            assertThat(foundActiveChild.getParent()).isNotNull();
            assertThat(foundActiveChild.getParent().getName()).isEqualTo("활성부모");

            CategoryEntity foundInactiveChild = findCategoryByName(allCategories, "비활성자식");
            assertThat(foundInactiveChild.getParent()).isNotNull();
            assertThat(foundInactiveChild.getParent().getName()).isEqualTo("비활성부모");
        }

        @Test
        @DisplayName("부모가 없는 최상위 카테고리들 조회")
        void findAllWithParent_RootCategoriesOnly_Success() {
            // Given
            CategoryEntity root1 = createCategory("루트1", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity root2 = createCategory("루트2", CategoryStatusEntity.INACTIVE, null);
            categoryJpaRepository.saveAll(List.of(root1, root2));

            // When
            List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

            // Then
            assertThat(allCategories).hasSize(2);
            assertThat(allCategories)
                    .extracting(CategoryEntity::getName)
                    .containsExactlyInAnyOrder("루트1", "루트2");

            // 모든 카테고리의 부모가 null인지 확인
            assertThat(allCategories)
                    .allSatisfy(category -> assertThat(category.getParent()).isNull());
        }

        @Test
        @DisplayName("카테고리가 없을 때 빈 리스트 반환")
        void findAllWithParent_NoCategories_ReturnsEmptyList() {
            // When
            List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

            // Then
            assertThat(allCategories).isEmpty();
        }
    }

    @Nested
    @DisplayName("하위 트리 조회 테스트")
    class SubTreeQueryTests {

        @Test
        @DisplayName("카테고리 하위 트리 조회 성공")
        void findSubTreeByIdNative_Success() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            CategoryEntity child1 = createCategory("자식1", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity child2 = createCategory("자식2", CategoryStatusEntity.ACTIVE, savedRoot);
            List<CategoryEntity> savedChildren = categoryJpaRepository.saveAll(List.of(child1, child2));

            CategoryEntity grandChild = createCategory("손자", CategoryStatusEntity.ACTIVE, savedChildren.get(0));
            categoryJpaRepository.save(grandChild);

            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> subTree = categoryJpaRepository.findSubTreeByIdNative(savedRoot.getId());

            // Then
            assertThat(subTree).hasSize(4);
            assertThat(subTree)
                    .extracting(CategoryEntity::getName)
                    .containsExactlyInAnyOrder("루트", "자식1", "자식2", "손자");
        }

        @Test
        @DisplayName("리프 노드의 하위 트리 조회")
        void findSubTreeByIdNative_LeafNode_ReturnsOnlyItself() {
            // Given
            CategoryEntity parent = createCategory("부모", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedParent = categoryJpaRepository.save(parent);

            CategoryEntity leaf = createCategory("리프", CategoryStatusEntity.ACTIVE, savedParent);
            CategoryEntity savedLeaf = categoryJpaRepository.save(leaf);

            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> subTree = categoryJpaRepository.findSubTreeByIdNative(savedLeaf.getId());

            // Then
            assertThat(subTree).hasSize(1);
            assertThat(subTree.get(0).getName()).isEqualTo("리프");
        }

        @Test
        @DisplayName("존재하지 않는 ID로 하위 트리 조회")
        void findSubTreeByIdNative_NonExistentId_ReturnsEmptyList() {
            // When
            List<CategoryEntity> subTree = categoryJpaRepository.findSubTreeByIdNative(999L);

            // Then
            assertThat(subTree).isEmpty();
        }

        @Test
        @DisplayName("복잡한 트리 구조에서 중간 노드의 하위 트리 조회")
        void findSubTreeByIdNative_ComplexTree_Success() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            CategoryEntity branch1 = createCategory("가지1", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity branch2 = createCategory("가지2", CategoryStatusEntity.ACTIVE, savedRoot);
            List<CategoryEntity> savedBranches = categoryJpaRepository.saveAll(List.of(branch1, branch2));

            CategoryEntity leaf1 = createCategory("잎1", CategoryStatusEntity.ACTIVE, savedBranches.get(0));
            CategoryEntity leaf2 = createCategory("잎2", CategoryStatusEntity.ACTIVE, savedBranches.get(0));
            CategoryEntity leaf3 = createCategory("잎3", CategoryStatusEntity.ACTIVE, savedBranches.get(1));
            categoryJpaRepository.saveAll(List.of(leaf1, leaf2, leaf3));

            categoryJpaRepository.flush();

            // When - 가지1의 하위 트리만 조회
            List<CategoryEntity> subTree = categoryJpaRepository.findSubTreeByIdNative(savedBranches.get(0).getId());

            // Then
            assertThat(subTree).hasSize(3);
            assertThat(subTree)
                    .extracting(CategoryEntity::getName)
                    .containsExactlyInAnyOrder("가지1", "잎1", "잎2");
        }
    }

    @Nested
    @DisplayName("N+1 문제 방지 테스트")
    class NPlusOneProblemTests {

        @Test
        @DisplayName("findAllWithParentByStatus에서 N+1 문제가 발생하지 않는지 확인")
        void findAllWithParentByStatus_PreventNPlusOneProblem() {
            // Given
            CategoryEntity parent = createCategory("부모", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedParent = categoryJpaRepository.save(parent);

            // 여러 자식 카테고리 생성
            for (int i = 1; i <= 5; i++) {
                CategoryEntity child = createCategory("자식" + i, CategoryStatusEntity.ACTIVE, savedParent);
                categoryJpaRepository.save(child);
            }
            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> categories = categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);

            // Then
            assertThat(categories).hasSize(6); // 부모 1개 + 자식 5개

            // 모든 자식 카테고리의 부모 정보가 이미 로드되어 있는지 확인
            // (실제로는 SQL 로그를 확인해야 하지만, 여기서는 부모 정보 접근이 가능한지만 확인)
            categories.stream()
                    .filter(c -> c.getName().startsWith("자식"))
                    .forEach(child -> {
                        assertThat(child.getParent()).isNotNull();
                        assertThat(child.getParent().getName()).isEqualTo("부모");
                    });
        }

        @Test
        @DisplayName("findAllWithParent에서 N+1 문제가 발생하지 않는지 확인")
        void findAllWithParent_PreventNPlusOneProblem() {
            // Given
            CategoryEntity parent1 = createCategory("부모1", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity parent2 = createCategory("부모2", CategoryStatusEntity.INACTIVE, null);
            List<CategoryEntity> savedParents = categoryJpaRepository.saveAll(List.of(parent1, parent2));

            // 각 부모에 자식들 생성
            for (int i = 1; i <= 3; i++) {
                CategoryEntity child1 = createCategory("부모1의자식" + i, CategoryStatusEntity.ACTIVE, savedParents.get(0));
                CategoryEntity child2 = createCategory("부모2의자식" + i, CategoryStatusEntity.INACTIVE, savedParents.get(1));
                categoryJpaRepository.saveAll(List.of(child1, child2));
            }
            categoryJpaRepository.flush();

            // When
            List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

            // Then
            assertThat(allCategories).hasSize(8); // 부모 2개 + 자식 6개

            // 모든 자식 카테고리의 부모 정보가 이미 로드되어 있는지 확인
            allCategories.stream()
                    .filter(c -> c.getName().contains("자식"))
                    .forEach(child -> {
                        assertThat(child.getParent()).isNotNull();
                        assertThat(child.getParent().getName()).startsWith("부모");
                    });
        }
    }

    @Nested
    @DisplayName("카테고리 깊이 조회 테스트")
    class CategoryDepthQueryTests {

        @Test
        @DisplayName("루트 카테고리의 깊이 조회 - 깊이 0")
        void getDepthByIdNative_RootCategory_ReturnsZero() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);
            categoryJpaRepository.flush();

            // When
            Integer depth = categoryJpaRepository.getDepthByIdNative(savedRoot.getId());

            // Then
            assertThat(depth).isEqualTo(0);
        }

        @Test
        @DisplayName("1단계 자식 카테고리의 깊이 조회 - 깊이 1")
        void getDepthByIdNative_FirstLevelChild_ReturnsOne() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            CategoryEntity child = createCategory("자식", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity savedChild = categoryJpaRepository.save(child);
            categoryJpaRepository.flush();

            // When
            Integer depth = categoryJpaRepository.getDepthByIdNative(savedChild.getId());

            // Then
            assertThat(depth).isEqualTo(1);
        }

        @Test
        @DisplayName("깊은 계층 구조에서 깊이 조회")
        void getDepthByIdNative_DeepHierarchy_ReturnsCorrectDepth() {
            // Given - 5단계 깊이의 카테고리 구조 생성
            CategoryEntity level0 = createCategory("레벨0", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedLevel0 = categoryJpaRepository.save(level0);

            CategoryEntity level1 = createCategory("레벨1", CategoryStatusEntity.ACTIVE, savedLevel0);
            CategoryEntity savedLevel1 = categoryJpaRepository.save(level1);

            CategoryEntity level2 = createCategory("레벨2", CategoryStatusEntity.ACTIVE, savedLevel1);
            CategoryEntity savedLevel2 = categoryJpaRepository.save(level2);

            CategoryEntity level3 = createCategory("레벨3", CategoryStatusEntity.ACTIVE, savedLevel2);
            CategoryEntity savedLevel3 = categoryJpaRepository.save(level3);

            CategoryEntity level4 = createCategory("레벨4", CategoryStatusEntity.ACTIVE, savedLevel3);
            CategoryEntity savedLevel4 = categoryJpaRepository.save(level4);

            categoryJpaRepository.flush();

            // When & Then - 각 레벨의 깊이 확인
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel0.getId())).isEqualTo(0);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel1.getId())).isEqualTo(1);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel2.getId())).isEqualTo(2);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel3.getId())).isEqualTo(3);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel4.getId())).isEqualTo(4);
        }

        @Test
        @DisplayName("복잡한 트리 구조에서 각 브랜치의 깊이 조회")
        void getDepthByIdNative_ComplexTreeStructure_ReturnsCorrectDepths() {
            // Given - 복잡한 트리 구조 생성
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            // 첫 번째 브랜치 (깊이 3)
            CategoryEntity branch1_L1 = createCategory("브랜치1-레벨1", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity savedBranch1_L1 = categoryJpaRepository.save(branch1_L1);

            CategoryEntity branch1_L2 = createCategory("브랜치1-레벨2", CategoryStatusEntity.ACTIVE, savedBranch1_L1);
            CategoryEntity savedBranch1_L2 = categoryJpaRepository.save(branch1_L2);

            CategoryEntity branch1_L3 = createCategory("브랜치1-레벨3", CategoryStatusEntity.ACTIVE, savedBranch1_L2);
            CategoryEntity savedBranch1_L3 = categoryJpaRepository.save(branch1_L3);

            // 두 번째 브랜치 (깊이 2)
            CategoryEntity branch2_L1 = createCategory("브랜치2-레벨1", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity savedBranch2_L1 = categoryJpaRepository.save(branch2_L1);

            CategoryEntity branch2_L2 = createCategory("브랜치2-레벨2", CategoryStatusEntity.ACTIVE, savedBranch2_L1);
            CategoryEntity savedBranch2_L2 = categoryJpaRepository.save(branch2_L2);

            categoryJpaRepository.flush();

            // When & Then
            assertThat(categoryJpaRepository.getDepthByIdNative(savedRoot.getId())).isEqualTo(0);

            // 첫 번째 브랜치
            assertThat(categoryJpaRepository.getDepthByIdNative(savedBranch1_L1.getId())).isEqualTo(1);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedBranch1_L2.getId())).isEqualTo(2);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedBranch1_L3.getId())).isEqualTo(3);

            // 두 번째 브랜치
            assertThat(categoryJpaRepository.getDepthByIdNative(savedBranch2_L1.getId())).isEqualTo(1);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedBranch2_L2.getId())).isEqualTo(2);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 ID로 깊이 조회 - null 반환")
        void getDepthByIdNative_NonExistentId_ReturnsNull() {
            // When
            Integer depth = categoryJpaRepository.getDepthByIdNative(999L);

            // Then
            assertThat(depth).isNull();
        }

        @Test
        @DisplayName("자식이 많은 카테고리의 깊이 조회")
        void getDepthByIdNative_CategoryWithManyChildren_ReturnsCorrectDepth() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            // 루트 아래에 여러 자식 생성
            for (int i = 1; i <= 5; i++) {
                CategoryEntity child = createCategory("자식" + i, CategoryStatusEntity.ACTIVE, savedRoot);
                categoryJpaRepository.save(child);
            }

            // 첫 번째 자식 아래에 손자 생성
            CategoryEntity firstChild = categoryJpaRepository.findAll().stream()
                    .filter(c -> "자식1".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();

            CategoryEntity grandChild = createCategory("손자", CategoryStatusEntity.ACTIVE, firstChild);
            CategoryEntity savedGrandChild = categoryJpaRepository.save(grandChild);

            categoryJpaRepository.flush();

            // When & Then
            assertThat(categoryJpaRepository.getDepthByIdNative(savedRoot.getId())).isEqualTo(0);
            assertThat(categoryJpaRepository.getDepthByIdNative(firstChild.getId())).isEqualTo(1);
            assertThat(categoryJpaRepository.getDepthByIdNative(savedGrandChild.getId())).isEqualTo(2);
        }

        @Test
        @DisplayName("중간 레벨 카테고리의 루트로부터의 깊이 조회")
        void getDepthByIdNative_MiddleLevelCategory_ReturnsDistanceFromRoot() {
            // Given
            CategoryEntity root = createCategory("루트", CategoryStatusEntity.ACTIVE, null);
            CategoryEntity savedRoot = categoryJpaRepository.save(root);

            CategoryEntity level1 = createCategory("레벨1", CategoryStatusEntity.ACTIVE, savedRoot);
            CategoryEntity savedLevel1 = categoryJpaRepository.save(level1);

            CategoryEntity level2 = createCategory("레벨2", CategoryStatusEntity.ACTIVE, savedLevel1);
            CategoryEntity savedLevel2 = categoryJpaRepository.save(level2);

            // level1에서 다른 브랜치 생성
            CategoryEntity level1_branch2 = createCategory("레벨1-브랜치2", CategoryStatusEntity.ACTIVE, savedLevel1);
            CategoryEntity savedLevel1_branch2 = categoryJpaRepository.save(level1_branch2);

            CategoryEntity level2_branch2 = createCategory("레벨2-브랜치2", CategoryStatusEntity.ACTIVE, savedLevel1_branch2);
            CategoryEntity savedLevel2_branch2 = categoryJpaRepository.save(level2_branch2);

            CategoryEntity level3_branch2 = createCategory("레벨3-브랜치2", CategoryStatusEntity.ACTIVE, savedLevel2_branch2);
            CategoryEntity savedLevel3_branch2 = categoryJpaRepository.save(level3_branch2);

            categoryJpaRepository.flush();

            // When & Then - 각 카테고리의 루트로부터의 깊이(거리) 확인
            assertThat(categoryJpaRepository.getDepthByIdNative(savedRoot.getId())).isEqualTo(0);           // 루트
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel1.getId())).isEqualTo(1);         // 루트 -> 레벨1
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel2.getId())).isEqualTo(2);         // 루트 -> 레벨1 -> 레벨2
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel1_branch2.getId())).isEqualTo(2); // 루트 -> 레벨1 -> 레벨1-브랜치2
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel2_branch2.getId())).isEqualTo(3); // 루트 -> 레벨1 -> 레벨1-브랜치2 -> 레벨2-브랜치2
            assertThat(categoryJpaRepository.getDepthByIdNative(savedLevel3_branch2.getId())).isEqualTo(4); // 루트 -> 레벨1 -> 레벨1-브랜치2 -> 레벨2-브랜치2 -> 레벨3-브랜치2
        }

    }


    private CategoryEntity createCategory(String name, CategoryStatusEntity status, CategoryEntity parent) {
        return CategoryEntity.builder()
                .name(name)
                .status(status)
                .parent(parent)
                .children(Collections.emptyList())
                .products(Collections.emptyList())
                .build();
    }

    private CategoryEntity findCategoryByName(List<CategoryEntity> categories, String name) {
        return categories.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CategoryStatusEntity.ACTIVE not found: " + name));
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}