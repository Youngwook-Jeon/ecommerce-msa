package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.OptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadOptionValueView;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionGroupQueryServiceTest {

    @Mock
    private OptionGroupReadRepository optionGroupReadRepository;

    @InjectMocks
    private OptionGroupQueryService optionGroupQueryService;

    @Test
    @DisplayName("getAllActiveOptionGroups: repository 결과를 그대로 반환한다")
    void getAllActiveOptionGroups_ReturnsRepositoryResult() {
        UUID id = UUID.randomUUID();
        List<ReadOptionGroupView> views = List.of(
                ReadOptionGroupView.builder()
                        .id(id)
                        .name("COLOR")
                        .displayName("색상")
                        .status(OptionStatus.ACTIVE)
                        .optionValues(List.of())
                        .build()
        );

        when(optionGroupReadRepository.findAllActiveCatalogOptionGroups()).thenReturn(views);

        List<ReadOptionGroupView> result = optionGroupQueryService.getAllActiveOptionGroups();

        assertThat(result).isSameAs(views);
        verify(optionGroupReadRepository).findAllActiveCatalogOptionGroups();
        verifyNoMoreInteractions(optionGroupReadRepository);
    }

    @Test
    @DisplayName("getActiveOptionGroupDetail: 존재하면 조회 결과를 반환")
    void getActiveOptionGroupDetail_Success() {
        UUID rawId = UUID.randomUUID();
        OptionGroupId groupId = new OptionGroupId(rawId);

        ReadOptionGroupView view = ReadOptionGroupView.builder()
                .id(rawId)
                .name("SIZE")
                .displayName("사이즈")
                .status(OptionStatus.ACTIVE)
                .optionValues(List.of(
                        ReadOptionValueView.builder()
                                .id(UUID.randomUUID())
                                .value("L")
                                .displayName("L")
                                .sortOrder(0)
                                .status(OptionStatus.ACTIVE)
                                .build()
                ))
                .build();

        when(optionGroupReadRepository.findActiveCatalogOptionGroupById(groupId)).thenReturn(Optional.of(view));

        ReadOptionGroupView result = optionGroupQueryService.getActiveOptionGroupDetail(groupId);

        assertThat(result).isSameAs(view);
        verify(optionGroupReadRepository).findActiveCatalogOptionGroupById(groupId);
    }

    @Test
    @DisplayName("getActiveOptionGroupDetail: 없거나 비활성/삭제면 OptionGroupNotFoundException")
    void getActiveOptionGroupDetail_NotFound_ThrowsException() {
        OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());
        when(optionGroupReadRepository.findActiveCatalogOptionGroupById(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> optionGroupQueryService.getActiveOptionGroupDetail(groupId))
                .isInstanceOf(OptionGroupNotFoundException.class)
                .hasMessageContaining("Option group not found or not active");

        verify(optionGroupReadRepository).findActiveCatalogOptionGroupById(groupId);
    }
}
