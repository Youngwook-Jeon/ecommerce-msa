package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionGroupRepositoryImplTest {

    @Mock
    private OptionGroupJpaRepository optionGroupJpaRepository;

    private OptionGroupDataAccessMapper optionGroupDataAccessMapper;

    private OptionGroupRepositoryImpl optionGroupRepository;

    @BeforeEach
    void setUp() {
        optionGroupDataAccessMapper = new OptionGroupDataAccessMapper();
        optionGroupRepository = new OptionGroupRepositoryImpl(optionGroupJpaRepository, optionGroupDataAccessMapper);
    }

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("null OptionGroup이면 IllegalArgumentException")
        void save_NullOptionGroup_ThrowsException() {
            assertThatThrownBy(() -> optionGroupRepository.save(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("optionGroup must not be null");

            verifyNoInteractions(optionGroupJpaRepository);
        }

        @Test
        @DisplayName("ID는 있으나 DB에 없으면 domainToEntity로 신규 저장")
        void save_NewIdNotInDatabase_UsesDomainToEntity() {
            UUID groupId = UUID.randomUUID();
            OptionGroup domain = OptionGroup.reconstitute(
                    new OptionGroupId(groupId),
                    "COLOR",
                    "색상",
                    OptionStatus.ACTIVE,
                    List.of()
            );

            when(optionGroupJpaRepository.findById(groupId)).thenReturn(Optional.empty());
            when(optionGroupJpaRepository.save(any(OptionGroupEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OptionGroup saved = optionGroupRepository.save(domain);

            assertThat(saved.getId().getValue()).isEqualTo(groupId);
            assertThat(saved.getName()).isEqualTo("COLOR");
            verify(optionGroupJpaRepository).findById(groupId);
            verify(optionGroupJpaRepository).save(any(OptionGroupEntity.class));
        }

        @Test
        @DisplayName("기존 행이 있으면 merge 후 저장한다")
        void save_ExistingRow_MergesAndSaves() {
            UUID groupId = UUID.randomUUID();
            UUID existingValueId = UUID.randomUUID();
            UUID newValueId = UUID.randomUUID();

            OptionGroupEntity existingEntity = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();

            OptionValueEntity existingValEntity = OptionValueEntity.builder()
                    .id(existingValueId)
                    .value("RED")
                    .displayName("빨강")
                    .sortOrder(0)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            existingEntity.addOptionValue(existingValEntity);

            OptionValue updatedDomVal = OptionValue.reconstitute(
                    new OptionValueId(existingValueId),
                    "RED",
                    "진한 빨강",
                    3,
                    OptionStatus.INACTIVE
            );
            OptionValue newDomVal = OptionValue.reconstitute(
                    new OptionValueId(newValueId),
                    "BLUE",
                    "파랑",
                    0,
                    OptionStatus.ACTIVE
            );

            OptionGroup domain = OptionGroup.reconstitute(
                    new OptionGroupId(groupId),
                    "COLOR_NEW",
                    "색상(변경)",
                    OptionStatus.INACTIVE,
                    List.of(updatedDomVal, newDomVal)
            );

            when(optionGroupJpaRepository.findById(groupId)).thenReturn(Optional.of(existingEntity));
            when(optionGroupJpaRepository.save(existingEntity)).thenReturn(existingEntity);

            OptionGroup result = optionGroupRepository.save(domain);

            assertThat(result.getName()).isEqualTo("COLOR_NEW");
            assertThat(result.getDisplayName()).isEqualTo("색상(변경)");
            assertThat(result.getStatus()).isEqualTo(OptionStatus.INACTIVE);
            assertThat(result.getOptionValues()).hasSize(2);

            assertThat(existingValEntity.getDisplayName()).isEqualTo("진한 빨강");
            assertThat(existingValEntity.getSortOrder()).isEqualTo(3);
            assertThat(existingValEntity.getStatus()).isEqualTo(OptionStatusEntity.INACTIVE);

            verify(optionGroupJpaRepository).save(existingEntity);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("null OptionGroupId이면 IllegalArgumentException")
        void findById_NullId_ThrowsException() {
            assertThatThrownBy(() -> optionGroupRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("optionGroupId must not be null");

            verifyNoInteractions(optionGroupJpaRepository);
        }

        @Test
        @DisplayName("조회 성공 시 도메인으로 매핑한다")
        void findById_Success() {
            UUID groupId = UUID.randomUUID();
            OptionGroupEntity entity = OptionGroupEntity.builder()
                    .id(groupId)
                    .name("SIZE")
                    .displayName("사이즈")
                    .status(OptionStatusEntity.ACTIVE)
                    .optionValues(new ArrayList<>())
                    .build();

            when(optionGroupJpaRepository.findById(groupId)).thenReturn(Optional.of(entity));

            Optional<OptionGroup> found = optionGroupRepository.findById(new OptionGroupId(groupId));

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("SIZE");
        }

        @Test
        @DisplayName("없으면 Optional.empty()")
        void findById_NotFound_ReturnsEmpty() {
            UUID groupId = UUID.randomUUID();
            when(optionGroupJpaRepository.findById(groupId)).thenReturn(Optional.empty());

            Optional<OptionGroup> found = optionGroupRepository.findById(new OptionGroupId(groupId));

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByName")
    class ExistsByNameTests {

        @Test
        @DisplayName("이름으로 존재 여부를 위임한다")
        void existsByName_Success() {
            when(optionGroupJpaRepository.existsByName("COLOR")).thenReturn(true);

            assertThat(optionGroupRepository.existsByName("COLOR")).isTrue();
            verify(optionGroupJpaRepository).existsByName("COLOR");
        }

        @Test
        @DisplayName("null 이름이면 IllegalArgumentException")
        void existsByName_Null_ThrowsException() {
            assertThatThrownBy(() -> optionGroupRepository.existsByName(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name must not be null or blank");

            verifyNoInteractions(optionGroupJpaRepository);
        }

        @Test
        @DisplayName("blank 이름이면 IllegalArgumentException")
        void existsByName_Blank_ThrowsException() {
            assertThatThrownBy(() -> optionGroupRepository.existsByName("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name must not be null or blank");

            verifyNoInteractions(optionGroupJpaRepository);
        }
    }
}
