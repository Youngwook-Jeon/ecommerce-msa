package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadOptionValueView;
import com.project.young.productservice.application.service.OptionGroupQueryService;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.ReadOptionGroupListQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionGroupQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionValueQueryResponse;
import com.project.young.productservice.web.mapper.OptionGroupQueryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OptionGroupQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class OptionGroupQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OptionGroupQueryService optionGroupQueryService;

    @MockitoBean
    private OptionGroupQueryResponseMapper optionGroupQueryResponseMapper;

    @Nested
    @DisplayName("GET /queries/option-groups")
    class GetAllActiveOptionGroupsTests {

        @Test
        @DisplayName("활성 옵션 그룹 목록 조회 시 200 OK")
        void getAllActiveOptionGroups_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            ReadOptionGroupView view = ReadOptionGroupView.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .optionValues(List.of())
                    .build();

            ReadOptionGroupQueryResponse item = ReadOptionGroupQueryResponse.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status("ACTIVE")
                    .optionValues(List.of())
                    .build();

            ReadOptionGroupListQueryResponse response = ReadOptionGroupListQueryResponse.builder()
                    .optionGroups(List.of(item))
                    .build();

            when(optionGroupQueryService.getAllActiveOptionGroups()).thenReturn(List.of(view));
            when(optionGroupQueryResponseMapper.toReadOptionGroupListQueryResponse(anyList()))
                    .thenReturn(response);

            mockMvc.perform(get("/queries/option-groups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.optionGroups[0].id").value(groupId.toString()))
                    .andExpect(jsonPath("$.optionGroups[0].name").value("COLOR"))
                    .andExpect(jsonPath("$.optionGroups[0].status").value("ACTIVE"));

            verify(optionGroupQueryService).getAllActiveOptionGroups();
            verify(optionGroupQueryResponseMapper).toReadOptionGroupListQueryResponse(anyList());
        }
    }

    @Nested
    @DisplayName("GET /queries/option-groups/{optionGroupId}")
    class GetActiveOptionGroupDetailTests {

        @Test
        @DisplayName("단일 옵션 그룹 상세 조회 시 200 OK")
        void getActiveOptionGroupDetail_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();

            ReadOptionGroupView view = ReadOptionGroupView.builder()
                    .id(groupId)
                    .name("SIZE")
                    .displayName("사이즈")
                    .status(OptionStatus.ACTIVE)
                    .optionValues(List.of(
                            ReadOptionValueView.builder()
                                    .id(valueId)
                                    .value("L")
                                    .displayName("라지")
                                    .sortOrder(0)
                                    .status(OptionStatus.ACTIVE)
                                    .build()
                    ))
                    .build();

            ReadOptionGroupQueryResponse response = ReadOptionGroupQueryResponse.builder()
                    .id(groupId)
                    .name("SIZE")
                    .displayName("사이즈")
                    .status("ACTIVE")
                    .optionValues(List.of(
                            ReadOptionValueQueryResponse.builder()
                                    .id(valueId)
                                    .value("L")
                                    .displayName("라지")
                                    .sortOrder(0)
                                    .status("ACTIVE")
                                    .build()
                    ))
                    .build();

            when(optionGroupQueryService.getActiveOptionGroupDetail(any())).thenReturn(view);
            when(optionGroupQueryResponseMapper.toReadOptionGroupQueryResponse(any(ReadOptionGroupView.class)))
                    .thenReturn(response);

            mockMvc.perform(get("/queries/option-groups/{optionGroupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.name").value("SIZE"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.optionValues[0].value").value("L"));

            verify(optionGroupQueryService).getActiveOptionGroupDetail(any());
            verify(optionGroupQueryResponseMapper).toReadOptionGroupQueryResponse(any(ReadOptionGroupView.class));
        }
    }
}
