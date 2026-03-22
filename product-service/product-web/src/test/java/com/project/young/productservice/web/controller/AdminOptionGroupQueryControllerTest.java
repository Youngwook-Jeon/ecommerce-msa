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
import org.springframework.security.test.context.support.WithMockUser;
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

@WebMvcTest(AdminOptionGroupQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class AdminOptionGroupQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OptionGroupQueryService optionGroupQueryService;

    @MockitoBean
    private OptionGroupQueryResponseMapper optionGroupQueryResponseMapper;

    @Nested
    @DisplayName("GET /admin/queries/option-groups")
    class GetAllTests {

        @Test
        @DisplayName("ADMIN 권한이면 200 OK")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID groupId = UUID.randomUUID();
            ReadOptionGroupView view = ReadOptionGroupView.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.INACTIVE)
                    .optionValues(List.of())
                    .build();

            ReadOptionGroupQueryResponse item = ReadOptionGroupQueryResponse.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status("INACTIVE")
                    .optionValues(List.of())
                    .build();

            ReadOptionGroupListQueryResponse response = ReadOptionGroupListQueryResponse.builder()
                    .optionGroups(List.of(item))
                    .build();

            when(optionGroupQueryService.getAllOptionGroupsForAdmin()).thenReturn(List.of(view));
            when(optionGroupQueryResponseMapper.toReadOptionGroupListQueryResponse(anyList()))
                    .thenReturn(response);

            mockMvc.perform(get("/admin/queries/option-groups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.optionGroups[0].id").value(groupId.toString()))
                    .andExpect(jsonPath("$.optionGroups[0].status").value("INACTIVE"));

            verify(optionGroupQueryService).getAllOptionGroupsForAdmin();
            verify(optionGroupQueryResponseMapper).toReadOptionGroupListQueryResponse(anyList());
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403")
        @WithMockUser(authorities = "CUSTOMER")
        void withoutAdmin_Returns403() throws Exception {
            mockMvc.perform(get("/admin/queries/option-groups"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(optionGroupQueryService);
        }
    }

    @Nested
    @DisplayName("GET /admin/queries/option-groups/{optionGroupId}")
    class GetDetailTests {

        @Test
        @DisplayName("ADMIN 권한이면 단건 상세 200 OK (비활성 값 포함 가능)")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
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
                                    .value("XL")
                                    .displayName("특대")
                                    .sortOrder(0)
                                    .status(OptionStatus.INACTIVE)
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
                                    .value("XL")
                                    .displayName("특대")
                                    .sortOrder(0)
                                    .status("INACTIVE")
                                    .build()
                    ))
                    .build();

            when(optionGroupQueryService.getOptionGroupDetailForAdmin(any())).thenReturn(view);
            when(optionGroupQueryResponseMapper.toReadOptionGroupQueryResponse(any(ReadOptionGroupView.class)))
                    .thenReturn(response);

            mockMvc.perform(get("/admin/queries/option-groups/{optionGroupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.optionValues[0].status").value("INACTIVE"));

            verify(optionGroupQueryService).getOptionGroupDetailForAdmin(any());
            verify(optionGroupQueryResponseMapper).toReadOptionGroupQueryResponse(any(ReadOptionGroupView.class));
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403")
        @WithMockUser(authorities = "CUSTOMER")
        void withoutAdmin_Returns403() throws Exception {
            UUID groupId = UUID.randomUUID();

            mockMvc.perform(get("/admin/queries/option-groups/{optionGroupId}", groupId))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(optionGroupQueryService);
        }
    }
}
