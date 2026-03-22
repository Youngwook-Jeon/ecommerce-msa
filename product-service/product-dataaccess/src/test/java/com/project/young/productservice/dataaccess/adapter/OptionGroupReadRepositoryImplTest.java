package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionGroupReadRepositoryImplTest {

    @Mock
    private OptionGroupJpaRepository optionGroupJpaRepository;

    private OptionGroupDataAccessMapper optionGroupDataAccessMapper;

    private OptionGroupReadRepositoryImpl optionGroupReadRepository;

    @BeforeEach
    void setUp() {
        optionGroupDataAccessMapper = new OptionGroupDataAccessMapper();
        optionGroupReadRepository = new OptionGroupReadRepositoryImpl(optionGroupJpaRepository, optionGroupDataAccessMapper);
    }

    @Nested
    @DisplayName("findAllActiveCatalogOptionGroups")
    class FindAllTests {

        @Test
        @DisplayName("ACTIVE 그룹만 조회하고 옵션 값은 ACTIVE만 sortOrder 순으로 매핑")
        void mapsGroupsAndFiltersActiveValuesOnly() {
            UUID groupId = UUID.randomUUID();
            UUID activeValId = UUID.randomUUID();
            UUID inactiveValId = UUID.randomUUID();

            OptionGroupEntity group = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();

            OptionValueEntity inactive = OptionValueEntity.builder()
                    .id(inactiveValId)
                    .value("OLD")
                    .displayName("구버전")
                    .sortOrder(0)
                    .status(OptionStatusEntity.INACTIVE)
                    .build();
            OptionValueEntity activeSecond = OptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .value("BLUE")
                    .displayName("파랑")
                    .sortOrder(2)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            OptionValueEntity activeFirst = OptionValueEntity.builder()
                    .id(activeValId)
                    .value("RED")
                    .displayName("빨강")
                    .sortOrder(1)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();

            group.addOptionValue(inactive);
            group.addOptionValue(activeSecond);
            group.addOptionValue(activeFirst);

            when(optionGroupJpaRepository.findAllByStatusOrderByNameAsc(OptionStatusEntity.ACTIVE))
                    .thenReturn(List.of(group));

            List<ReadOptionGroupView> result = optionGroupReadRepository.findAllActiveCatalogOptionGroups();

            assertThat(result).hasSize(1);
            ReadOptionGroupView view = result.get(0);
            assertThat(view.id()).isEqualTo(groupId);
            assertThat(view.status()).isEqualTo(OptionStatus.ACTIVE);
            assertThat(view.optionValues()).hasSize(2);
            assertThat(view.optionValues().get(0).value()).isEqualTo("RED");
            assertThat(view.optionValues().get(0).sortOrder()).isEqualTo(1);
            assertThat(view.optionValues().get(1).value()).isEqualTo("BLUE");

            verify(optionGroupJpaRepository).findAllByStatusOrderByNameAsc(OptionStatusEntity.ACTIVE);
        }
    }

    @Nested
    @DisplayName("findActiveCatalogOptionGroupById")
    class FindByIdTests {

        @Test
        @DisplayName("optionGroupId가 null이면 IllegalArgumentException")
        void nullId_Throws() {
            assertThatThrownBy(() -> optionGroupReadRepository.findActiveCatalogOptionGroupById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("optionGroupId must not be null");

            verifyNoInteractions(optionGroupJpaRepository);
        }

        @Test
        @DisplayName("ACTIVE 그룹이면 Optional.of 로 매핑")
        void found_ReturnsMappedView() {
            UUID groupId = UUID.randomUUID();
            OptionGroupEntity group = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("SIZE")
                    .displayName("사이즈")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();

            when(optionGroupJpaRepository.findByIdAndStatus(groupId, OptionStatusEntity.ACTIVE))
                    .thenReturn(Optional.of(group));

            Optional<ReadOptionGroupView> result =
                    optionGroupReadRepository.findActiveCatalogOptionGroupById(new OptionGroupId(groupId));

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("SIZE");
            verify(optionGroupJpaRepository).findByIdAndStatus(groupId, OptionStatusEntity.ACTIVE);
        }

        @Test
        @DisplayName("없거나 비활성/삭제면 Optional.empty()")
        void notFound_ReturnsEmpty() {
            UUID groupId = UUID.randomUUID();
            when(optionGroupJpaRepository.findByIdAndStatus(groupId, OptionStatusEntity.ACTIVE))
                    .thenReturn(Optional.empty());

            Optional<ReadOptionGroupView> result =
                    optionGroupReadRepository.findActiveCatalogOptionGroupById(new OptionGroupId(groupId));

            assertThat(result).isEmpty();
        }
    }
}
