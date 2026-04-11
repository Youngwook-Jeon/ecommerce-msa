package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddOptionValuesCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionValueCommand;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.mapper.OptionGroupDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.exception.DuplicateOptionGroupNameException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.service.OptionGroupDomainService;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionGroupApplicationServiceTest {

    @Mock
    private OptionGroupRepository optionGroupRepository;

    @Mock
    private OptionGroupDomainService optionGroupDomainService;

    @Mock
    private OptionGroupDataMapper optionGroupDataMapper;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private OptionGroupApplicationService optionGroupApplicationService;

    private static OptionGroup activeGroup(UUID groupId, List<OptionValue> optionValues) {
        return OptionGroup.reconstitute(
                new OptionGroupId(groupId),
                "COLOR",
                "색상",
                OptionStatus.ACTIVE,
                optionValues != null ? optionValues : List.of()
        );
    }

    private static OptionGroup deletedGroup(UUID groupId) {
        return OptionGroup.reconstitute(
                new OptionGroupId(groupId),
                "COLOR",
                "색상",
                OptionStatus.DELETED,
                List.of()
        );
    }

    @Nested
    @DisplayName("createOptionGroup")
    class CreateOptionGroupTests {

        @Test
        @DisplayName("command가 null이면 IllegalArgumentException")
        void createOptionGroup_NullCommand_ThrowsException() {
            assertThatThrownBy(() -> optionGroupApplicationService.createOptionGroup(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Create option group command cannot be null");

            verifyNoInteractions(optionGroupDomainService, optionGroupRepository, optionGroupDataMapper, idGenerator);
        }

        @Test
        @DisplayName("이름이 이미 존재하면 DuplicateOptionGroupNameException")
        void createOptionGroup_DuplicateName_ThrowsException() {
            CreateOptionGroupCommand command = CreateOptionGroupCommand.builder()
                    .name("COLOR")
                    .displayName("색상")
                    .build();

            when(optionGroupDomainService.isValidOptionGroupName("COLOR")).thenReturn(false);

            assertThatThrownBy(() -> optionGroupApplicationService.createOptionGroup(command))
                    .isInstanceOf(DuplicateOptionGroupNameException.class)
                    .hasMessageContaining("COLOR");

            verify(optionGroupDomainService).isValidOptionGroupName("COLOR");
            verify(optionGroupRepository, never()).update(any());
            verify(optionGroupDataMapper, never()).toOptionGroup(any(), any());
        }

        @Test
        @DisplayName("정상 생성 시 mapper와 repository를 통해 OptionGroup 생성")
        void createOptionGroup_Success() {
            CreateOptionGroupCommand command = CreateOptionGroupCommand.builder()
                    .name("SIZE")
                    .displayName("사이즈")
                    .build();

            UUID generatedId = UUID.randomUUID();
            OptionGroupId newGroupId = new OptionGroupId(generatedId);

            OptionGroup toSave = OptionGroup.builder()
                    .id(newGroupId)
                    .name("SIZE")
                    .displayName("사이즈")
                    .build();

            CreateOptionGroupResult expected = CreateOptionGroupResult.builder()
                    .id(generatedId)
                    .name("SIZE")
                    .build();

            when(optionGroupDomainService.isValidOptionGroupName("SIZE")).thenReturn(true);
            when(idGenerator.generateId()).thenReturn(generatedId);
            when(optionGroupDataMapper.toOptionGroup(command, newGroupId)).thenReturn(toSave);
            doNothing().when(optionGroupRepository).insert(toSave);
            when(optionGroupDataMapper.toCreateOptionGroupResult(toSave)).thenReturn(expected);

            CreateOptionGroupResult result = optionGroupApplicationService.createOptionGroup(command);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(generatedId);
            assertThat(result.name()).isEqualTo("SIZE");

            verify(optionGroupDomainService).isValidOptionGroupName("SIZE");
            verify(idGenerator).generateId();
            verify(optionGroupRepository).insert(toSave);
            verify(optionGroupDataMapper).toCreateOptionGroupResult(toSave);
        }
    }

    @Nested
    @DisplayName("addOptionValues")
    class AddOptionValuesTests {

        @Test
        @DisplayName("groupId 또는 command가 null이면 IllegalArgumentException")
        void addOptionValues_InvalidRequest_ThrowsException() {
            AddOptionValuesCommand command = AddOptionValuesCommand.builder()
                    .optionValues(List.of(
                            AddOptionValueCommand.builder()
                                    .value("RED")
                                    .displayName("빨강")
                                    .sortOrder(0)
                                    .build()
                    ))
                    .build();

            assertThatThrownBy(() -> optionGroupApplicationService.addOptionValues(null, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Add option values command cannot be null");

            UUID groupId = UUID.randomUUID();
            assertThatThrownBy(() -> optionGroupApplicationService.addOptionValues(groupId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Add option values command cannot be null");
        }

        @Test
        @DisplayName("옵션 값이 비어 있으면 IllegalArgumentException")
        void addOptionValues_EmptyValues_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            AddOptionValuesCommand command = AddOptionValuesCommand.builder()
                    .optionValues(List.of())
                    .build();

            assertThatThrownBy(() -> optionGroupApplicationService.addOptionValues(groupId, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one option value");
        }

        @Test
        @DisplayName("그룹이 없으면 OptionGroupNotFoundException")
        void addOptionValues_NotFound_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            AddOptionValuesCommand command = AddOptionValuesCommand.builder()
                    .optionValues(List.of(
                            AddOptionValueCommand.builder()
                                    .value("RED")
                                    .displayName("빨강")
                                    .sortOrder(0)
                                    .build()
                    ))
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionGroupApplicationService.addOptionValues(groupId, command))
                    .isInstanceOf(OptionGroupNotFoundException.class)
                    .hasMessageContaining("Option group not found");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("삭제된 그룹이면 OptionDomainException")
        void addOptionValues_DeletedGroup_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            AddOptionValuesCommand command = AddOptionValuesCommand.builder()
                    .optionValues(List.of(
                            AddOptionValueCommand.builder()
                                    .value("RED")
                                    .displayName("빨강")
                                    .sortOrder(0)
                                    .build()
                    ))
                    .build();

            OptionGroup deleted = deletedGroup(groupId);
            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> optionGroupApplicationService.addOptionValues(groupId, command))
                    .isInstanceOf(OptionDomainException.class)
                    .hasMessageContaining("Cannot modify an option group that has been deleted");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("여러 옵션 값을 한 번에 추가하고 update는 1번만 호출")
        void addOptionValues_Success() {
            UUID groupId = UUID.randomUUID();
            UUID valueId1 = UUID.randomUUID();
            UUID valueId2 = UUID.randomUUID();

            AddOptionValueCommand cmd1 = AddOptionValueCommand.builder()
                    .value("BLUE")
                    .displayName("파랑")
                    .sortOrder(1)
                    .build();
            AddOptionValueCommand cmd2 = AddOptionValueCommand.builder()
                    .value("GREEN")
                    .displayName("초록")
                    .sortOrder(2)
                    .build();

            AddOptionValuesCommand bulkCommand = AddOptionValuesCommand.builder()
                    .optionValues(List.of(cmd1, cmd2))
                    .build();

            OptionGroup optionGroup = activeGroup(groupId, new ArrayList<>());

            OptionValue newValue1 = OptionValue.reconstitute(
                    new OptionValueId(valueId1),
                    "BLUE",
                    "파랑",
                    1,
                    OptionStatus.ACTIVE
            );
            OptionValue newValue2 = OptionValue.reconstitute(
                    new OptionValueId(valueId2),
                    "GREEN",
                    "초록",
                    2,
                    OptionStatus.ACTIVE
            );

            AddOptionValueResult expected1 = AddOptionValueResult.builder()
                    .id(valueId1)
                    .value("BLUE")
                    .build();
            AddOptionValueResult expected2 = AddOptionValueResult.builder()
                    .id(valueId2)
                    .value("GREEN")
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.of(optionGroup));
            when(idGenerator.generateId()).thenReturn(valueId1, valueId2);

            when(optionGroupDataMapper.toOptionValue(cmd1, new OptionValueId(valueId1))).thenReturn(newValue1);
            when(optionGroupDataMapper.toOptionValue(cmd2, new OptionValueId(valueId2))).thenReturn(newValue2);

            when(optionGroupDataMapper.toAddOptionValueResult(newValue1)).thenReturn(expected1);
            when(optionGroupDataMapper.toAddOptionValueResult(newValue2)).thenReturn(expected2);

            doNothing().when(optionGroupRepository).update(optionGroup);

            List<AddOptionValueResult> results = optionGroupApplicationService.addOptionValues(groupId, bulkCommand);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).id()).isEqualTo(valueId1);
            assertThat(results.get(1).id()).isEqualTo(valueId2);

            verify(optionGroupRepository).update(optionGroup);
            verify(optionGroupDataMapper).toAddOptionValueResult(newValue1);
            verify(optionGroupDataMapper).toAddOptionValueResult(newValue2);
        }
    }

    @Nested
    @DisplayName("updateOptionGroup")
    class UpdateOptionGroupTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void updateOptionGroup_InvalidRequest_ThrowsException() {
            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionGroup(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid option group update request");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("대상이 없으면 OptionGroupNotFoundException")
        void updateOptionGroup_NotFound_ThrowsException() {
            UUID rawId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionGroup(rawId, command))
                    .isInstanceOf(OptionGroupNotFoundException.class)
                    .hasMessageContaining("Option group not found");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("삭제된 그룹이면 OptionDomainException")
        void updateOptionGroup_DeletedGroup_ThrowsException() {
            UUID rawId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("NEW")
                    .displayName("새표시")
                    .status(OptionStatus.INACTIVE)
                    .build();

            OptionGroup deleted = deletedGroup(rawId);
            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionGroup(rawId, command))
                    .isInstanceOf(OptionDomainException.class)
                    .hasMessageContaining("Cannot modify an option group that has been deleted");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("이름 변경 시 중복이면 DuplicateOptionGroupNameException")
        void updateOptionGroup_DuplicateName_ThrowsException() {
            UUID rawId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("DUPLICATE")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .build();

            OptionGroup optionGroup = activeGroup(rawId, List.of());
            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.of(optionGroup));
            when(optionGroupDomainService.isValidOptionGroupName("DUPLICATE")).thenReturn(false);

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionGroup(rawId, command))
                    .isInstanceOf(DuplicateOptionGroupNameException.class)
                    .hasMessageContaining("DUPLICATE");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("필드 변경 시 저장하고 UpdateOptionGroupResult 반환")
        void updateOptionGroup_ChangeFields_SavesAndReturnsResult() {
            UUID rawId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("NEWNAME")
                    .displayName("새노출명")
                    .status(OptionStatus.INACTIVE)
                    .build();

            OptionGroup optionGroup = activeGroup(rawId, List.of());
            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.of(optionGroup));
            when(optionGroupDomainService.isValidOptionGroupName("NEWNAME")).thenReturn(true);
            doNothing().when(optionGroupRepository).update(optionGroup);

            UpdateOptionGroupResult expected = UpdateOptionGroupResult.builder()
                    .id(rawId)
                    .name("NEWNAME")
                    .displayName("새노출명")
                    .status(OptionStatus.INACTIVE)
                    .build();
            when(optionGroupDataMapper.toUpdateOptionGroupResult(optionGroup)).thenReturn(expected);

            UpdateOptionGroupResult result = optionGroupApplicationService.updateOptionGroup(rawId, command);

            assertThat(result.name()).isEqualTo("NEWNAME");
            assertThat(result.displayName()).isEqualTo("새노출명");
            assertThat(result.status()).isEqualTo(OptionStatus.INACTIVE);

            verify(optionGroupRepository).update(optionGroup);
            verify(optionGroupDataMapper).toUpdateOptionGroupResult(optionGroup);
        }

        @Test
        @DisplayName("변경 사항이 없으면 save 호출하지 않음")
        void updateOptionGroup_NoChange_DoesNotSave() {
            UUID rawId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .build();

            OptionGroup optionGroup = activeGroup(rawId, List.of());
            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.of(optionGroup));

            UpdateOptionGroupResult expected = UpdateOptionGroupResult.builder()
                    .id(rawId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .build();
            when(optionGroupDataMapper.toUpdateOptionGroupResult(optionGroup)).thenReturn(expected);

            UpdateOptionGroupResult result = optionGroupApplicationService.updateOptionGroup(rawId, command);

            assertThat(result.status()).isEqualTo(OptionStatus.ACTIVE);
            verify(optionGroupRepository, never()).update(any());
            verify(optionGroupDataMapper).toUpdateOptionGroupResult(optionGroup);
        }
    }

    @Nested
    @DisplayName("updateOptionValue")
    class UpdateOptionValueTests {

        @Test
        @DisplayName("요청이 유효하지 않으면 IllegalArgumentException")
        void updateOptionValue_InvalidRequest_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            UpdateOptionValueCommand command = UpdateOptionValueCommand.builder()
                    .value("VAL")
                    .displayName("표시명")
                    .sortOrder(0)
                    .status(OptionStatus.ACTIVE)
                    .build();

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionValue(null, valueId, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid option value update request");

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionValue(groupId, null, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid option value update request");

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionValue(groupId, valueId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid option value update request");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("그룹이 없으면 OptionGroupNotFoundException")
        void updateOptionValue_GroupNotFound_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            UpdateOptionValueCommand command = UpdateOptionValueCommand.builder()
                    .value("RED")
                    .displayName("빨강")
                    .sortOrder(0)
                    .status(OptionStatus.ACTIVE)
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionGroupApplicationService.updateOptionValue(groupId, valueId, command))
                    .isInstanceOf(OptionGroupNotFoundException.class);

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("정상 수정 시 save 후 UpdateOptionValueResult 반환")
        void updateOptionValue_Success() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();

            OptionValue existing = OptionValue.reconstitute(
                    new OptionValueId(valueId),
                    "RED",
                    "빨강",
                    0,
                    OptionStatus.ACTIVE
            );
            List<OptionValue> values = new ArrayList<>();
            values.add(existing);
            OptionGroup optionGroup = activeGroup(groupId, values);

            UpdateOptionValueCommand command = UpdateOptionValueCommand.builder()
                    .value("RED")
                    .displayName("진한 빨강")
                    .sortOrder(3)
                    .status(OptionStatus.INACTIVE)
                    .build();

            UpdateOptionValueResult expected = new UpdateOptionValueResult(
                    valueId,
                    "RED",
                    "진한 빨강",
                    3,
                    OptionStatus.INACTIVE
            );

            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.of(optionGroup));
            doNothing().when(optionGroupRepository).update(optionGroup);
            when(optionGroupDataMapper.toUpdateOptionValueResult(existing)).thenReturn(expected);

            UpdateOptionValueResult result = optionGroupApplicationService.updateOptionValue(groupId, valueId, command);

            assertThat(result.displayName()).isEqualTo("진한 빨강");
            assertThat(result.sortOrder()).isEqualTo(3);
            assertThat(result.status()).isEqualTo(OptionStatus.INACTIVE);

            verify(optionGroupRepository).update(optionGroup);
            verify(optionGroupDataMapper).toUpdateOptionValueResult(existing);
        }
    }

    @Nested
    @DisplayName("deleteOptionGroup")
    class DeleteOptionGroupTests {

        @Test
        @DisplayName("null id이면 IllegalArgumentException")
        void deleteOptionGroup_NullId_ThrowsException() {
            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionGroup(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Option group ID for delete cannot be null");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("그룹이 없으면 OptionGroupNotFoundException")
        void deleteOptionGroup_NotFound_ThrowsException() {
            UUID rawId = UUID.randomUUID();
            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionGroup(rawId))
                    .isInstanceOf(OptionGroupNotFoundException.class);

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("삭제 성공 시 DeleteOptionGroupResult 반환")
        void deleteOptionGroup_Success() {
            UUID rawId = UUID.randomUUID();
            OptionGroup optionGroup = activeGroup(rawId, List.of());

            DeleteOptionGroupResult expected = DeleteOptionGroupResult.builder()
                    .id(rawId)
                    .name("COLOR")
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(rawId))).thenReturn(Optional.of(optionGroup));
            doNothing().when(optionGroupRepository).update(any(OptionGroup.class));
            when(optionGroupDataMapper.toDeleteOptionGroupResult(any(OptionGroup.class))).thenReturn(expected);

            DeleteOptionGroupResult result = optionGroupApplicationService.deleteOptionGroup(rawId);

            assertThat(result.id()).isEqualTo(rawId);
            assertThat(result.name()).isEqualTo("COLOR");

            verify(optionGroupRepository).update(any(OptionGroup.class));
            verify(optionGroupDataMapper).toDeleteOptionGroupResult(any(OptionGroup.class));
        }
    }

    @Nested
    @DisplayName("deleteOptionValue")
    class DeleteOptionValueTests {

        @Test
        @DisplayName("ID가 null이면 IllegalArgumentException")
        void deleteOptionValue_NullIds_ThrowsException() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionValue(null, id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IDs for deleting an option value cannot be null");

            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionValue(id, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IDs for deleting an option value cannot be null");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("그룹이 없으면 OptionGroupNotFoundException")
        void deleteOptionValue_GroupNotFound_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionValue(groupId, valueId))
                    .isInstanceOf(OptionGroupNotFoundException.class);

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("삭제된 그룹이면 OptionDomainException")
        void deleteOptionValue_DeletedGroup_ThrowsException() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            OptionGroup deleted = deletedGroup(groupId);
            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> optionGroupApplicationService.deleteOptionValue(groupId, valueId))
                    .isInstanceOf(OptionDomainException.class)
                    .hasMessageContaining("Cannot modify an option group that has been deleted");

            verify(optionGroupRepository, never()).update(any());
        }

        @Test
        @DisplayName("정상 삭제 시 save 후 DeleteOptionValueResult 반환")
        void deleteOptionValue_Success() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();

            OptionValue existing = OptionValue.reconstitute(
                    new OptionValueId(valueId),
                    "GREEN",
                    "초록",
                    0,
                    OptionStatus.ACTIVE
            );
            List<OptionValue> values = new ArrayList<>();
            values.add(existing);
            OptionGroup optionGroup = activeGroup(groupId, values);

            DeleteOptionValueResult expected = DeleteOptionValueResult.builder()
                    .id(valueId)
                    .value("GREEN")
                    .build();

            when(optionGroupRepository.findById(new OptionGroupId(groupId))).thenReturn(Optional.of(optionGroup));
            doNothing().when(optionGroupRepository).update(optionGroup);
            when(optionGroupDataMapper.toDeleteOptionValueResult(existing)).thenReturn(expected);

            DeleteOptionValueResult result = optionGroupApplicationService.deleteOptionValue(groupId, valueId);

            assertThat(result.id()).isEqualTo(valueId);
            assertThat(result.value()).isEqualTo("GREEN");
            assertThat(existing.getStatus()).isEqualTo(OptionStatus.DELETED);

            verify(optionGroupRepository).update(optionGroup);
            verify(optionGroupDataMapper).toDeleteOptionValueResult(existing);
        }
    }
}
