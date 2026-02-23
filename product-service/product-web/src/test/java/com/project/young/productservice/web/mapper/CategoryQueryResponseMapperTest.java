package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.web.converter.CategoryStatusWebConverter;
import com.project.young.productservice.web.dto.ReadCategoryNodeResponse;
import com.project.young.productservice.web.dto.ReadCategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryQueryResponseMapperTest {

    private CategoryQueryResponseMapper categoryQueryResponseMapper;

    @BeforeEach
    void setUp() {
        categoryQueryResponseMapper = new CategoryQueryResponseMapper(new CategoryStatusWebConverter());
    }

    @Nested
    @DisplayName("toReadCategoryResponse")
    class ToReadCategoryResponseTests {

        @Test
        @DisplayName("readCategoryViews가 null이면 NullPointerException")
        void nullList_ThrowsNullPointerException() {
            assertThatThrownBy(() -> categoryQueryResponseMapper.toReadCategoryResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ReadCategoryViews is null");
        }

        @Test
        @DisplayName("빈 리스트면 빈 categories로 반환")
        void emptyList_ReturnsEmptyCategories() {
            ReadCategoryResponse response = categoryQueryResponseMapper.toReadCategoryResponse(List.of());

            assertThat(response).isNotNull();
            assertThat(response.categories()).isEmpty();
        }

        @Test
        @DisplayName("단일 루트 노드 정상 매핑")
        void singleRootNode_Success() {
            ReadCategoryView view = ReadCategoryView.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .children(List.of())
                    .build();

            ReadCategoryResponse response = categoryQueryResponseMapper.toReadCategoryResponse(List.of(view));

            assertThat(response).isNotNull();
            assertThat(response.categories()).hasSize(1);
            assertThat(response.categories().get(0).id()).isEqualTo(1L);
            assertThat(response.categories().get(0).name()).isEqualTo("전자제품");
            assertThat(response.categories().get(0).parentId()).isNull();
            assertThat(response.categories().get(0).status()).isEqualTo("ACTIVE");
            assertThat(response.categories().get(0).children()).isEmpty();
        }

        @Test
        @DisplayName("계층 구조(부모-자식) 정상 매핑")
        void hierarchy_Success() {
            ReadCategoryView child = ReadCategoryView.builder()
                    .id(2L)
                    .name("스마트폰")
                    .parentId(1L)
                    .status(CategoryStatus.ACTIVE)
                    .children(List.of())
                    .build();

            ReadCategoryView parent = ReadCategoryView.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .children(List.of(child))
                    .build();

            ReadCategoryResponse response = categoryQueryResponseMapper.toReadCategoryResponse(List.of(parent));

            assertThat(response).isNotNull();
            assertThat(response.categories()).hasSize(1);
            ReadCategoryNodeResponse root = response.categories().get(0);
            assertThat(root.id()).isEqualTo(1L);
            assertThat(root.name()).isEqualTo("전자제품");
            assertThat(root.children()).hasSize(1);
            assertThat(root.children().get(0).id()).isEqualTo(2L);
            assertThat(root.children().get(0).name()).isEqualTo("스마트폰");
            assertThat(root.children().get(0).status()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("toReadCategoryNodeResponse")
    class ToReadCategoryNodeResponseTests {

        @Test
        @DisplayName("readCategoryView가 null이면 NullPointerException")
        void nullView_ThrowsNullPointerException() {
            assertThatThrownBy(() -> categoryQueryResponseMapper.toReadCategoryNodeResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ReadCategoryView is null");
        }

        @Test
        @DisplayName("정상 매핑")
        void success() {
            ReadCategoryView view = ReadCategoryView.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.INACTIVE)
                    .children(List.of())
                    .build();

            ReadCategoryNodeResponse response = categoryQueryResponseMapper.toReadCategoryNodeResponse(view);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("전자제품");
            assertThat(response.parentId()).isNull();
            assertThat(response.status()).isEqualTo("INACTIVE");
            assertThat(response.children()).isEmpty();
        }
    }
}