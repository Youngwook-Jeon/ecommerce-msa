package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.command.*;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.service.OptionGroupApplicationService;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.*;
import com.project.young.productservice.web.mapper.OptionGroupResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OptionGroupController.class)
@Import({SecurityConfig.class, TestConfig.class})
class OptionGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OptionGroupApplicationService optionGroupApplicationService;

    @MockitoBean
    private OptionGroupResponseMapper optionGroupResponseMapper;

    @Nested
    @DisplayName("POST /option-groups")
    @WithMockUser(authorities = "ADMIN")
    class CreateOptionGroupTests {

        @Test
        @DisplayName("옵션 그룹 생성 성공 시 201 Created")
        void createGroup_Success() throws Exception {
            CreateOptionGroupCommand command = CreateOptionGroupCommand.builder()
                    .name("COLOR")
                    .displayName("색상")
                    .build();

            UUID groupId = UUID.randomUUID();
            CreateOptionGroupResult result = CreateOptionGroupResult.builder()
                    .id(groupId)
                    .name("COLOR")
                    .build();

            CreateOptionGroupResponse response = CreateOptionGroupResponse.builder()
                    .id(groupId)
                    .name("COLOR")
                    .message("Option Group created successfully")
                    .build();

            when(optionGroupApplicationService.createOptionGroup(any())).thenReturn(result);
            when(optionGroupResponseMapper.toCreateOptionGroupResponse(any())).thenReturn(response);

            mockMvc.perform(post("/option-groups")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.name").value("COLOR"));

            verify(optionGroupApplicationService).createOptionGroup(any());
            verify(optionGroupResponseMapper).toCreateOptionGroupResponse(any());
        }
    }

    @Nested
    @DisplayName("PUT /option-groups/{groupId}")
    @WithMockUser(authorities = "ADMIN")
    class UpdateOptionGroupTests {

        @Test
        @DisplayName("옵션 그룹 수정 성공 시 200 OK")
        void updateGroup_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            UpdateOptionGroupCommand command = UpdateOptionGroupCommand.builder()
                    .name("COLOR_UPDATED")
                    .displayName("색상 수정")
                    .status(OptionStatus.ACTIVE)
                    .build();

            UpdateOptionGroupResult result = UpdateOptionGroupResult.builder()
                    .id(groupId)
                    .name("COLOR_UPDATED")
                    .displayName("색상 수정")
                    .status(OptionStatus.ACTIVE)
                    .build();

            UpdateOptionGroupResponse response = UpdateOptionGroupResponse.builder()
                    .id(groupId)
                    .name("COLOR_UPDATED")
                    .displayName("색상 수정")
                    .status("ACTIVE")
                    .message("Option Group updated successfully")
                    .build();

            when(optionGroupApplicationService.updateOptionGroup(eq(groupId), any())).thenReturn(result);
            when(optionGroupResponseMapper.toUpdateOptionGroupResponse(any())).thenReturn(response);

            mockMvc.perform(put("/option-groups/{groupId}", groupId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.name").value("COLOR_UPDATED"));

            verify(optionGroupApplicationService).updateOptionGroup(eq(groupId), any());
            verify(optionGroupResponseMapper).toUpdateOptionGroupResponse(any());
        }
    }

    @Nested
    @DisplayName("DELETE /option-groups/{groupId}")
    @WithMockUser(authorities = "ADMIN")
    class DeleteOptionGroupTests {

        @Test
        @DisplayName("옵션 그룹 삭제 성공 시 200 OK")
        void deleteGroup_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            DeleteOptionGroupResult result = DeleteOptionGroupResult.builder()
                    .id(groupId)
                    .name("COLOR")
                    .build();

            DeleteOptionGroupResponse response = DeleteOptionGroupResponse.builder()
                    .id(groupId)
                    .name("COLOR")
                    .message("Option Group deleted successfully")
                    .build();

            when(optionGroupApplicationService.deleteOptionGroup(groupId)).thenReturn(result);
            when(optionGroupResponseMapper.toDeleteOptionGroupResponse(any())).thenReturn(response);

            mockMvc.perform(delete("/option-groups/{groupId}", groupId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.message").value("Option Group deleted successfully"));

            verify(optionGroupApplicationService).deleteOptionGroup(groupId);
            verify(optionGroupResponseMapper).toDeleteOptionGroupResponse(any());
        }
    }

    @Nested
    @DisplayName("POST /option-groups/{groupId}/option-values")
    @WithMockUser(authorities = "ADMIN")
    class AddOptionValuesTests {

        @Test
        @DisplayName("옵션 값 추가 성공 시 201 Created")
        void addValues_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            AddOptionValuesCommand command = AddOptionValuesCommand.builder()
                    .optionValues(List.of(
                            AddOptionValueCommand.builder()
                                    .value("RED")
                                    .displayName("빨강")
                                    .sortOrder(1)
                                    .build()
                    ))
                    .build();

            List<AddOptionValueResult> results = List.of(
                    AddOptionValueResult.builder()
                            .id(UUID.randomUUID())
                            .value("RED")
                            .build()
            );

            AddOptionValuesResponse response = AddOptionValuesResponse.builder()
                    .optionValues(List.of(
                            AddOptionValueResponse.builder()
                                    .id(results.get(0).id())
                                    .value("RED")
                                    .message("Option Value added successfully")
                                    .build()
                    ))
                    .build();

            when(optionGroupApplicationService.addOptionValues(eq(groupId), any())).thenReturn(results);
            when(optionGroupResponseMapper.toAddOptionValuesResponse(any())).thenReturn(response);

            mockMvc.perform(post("/option-groups/{groupId}/option-values", groupId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.optionValues[0].value").value("RED"));

            verify(optionGroupApplicationService).addOptionValues(eq(groupId), any());
            verify(optionGroupResponseMapper).toAddOptionValuesResponse(any());
        }
    }

    @Nested
    @DisplayName("PUT /option-groups/{groupId}/option-values/{valueId}")
    @WithMockUser(authorities = "ADMIN")
    class UpdateOptionValueTests {

        @Test
        @DisplayName("옵션 값 수정 성공 시 200 OK")
        void updateValue_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            UpdateOptionValueCommand command = UpdateOptionValueCommand.builder()
                    .value("BLUE")
                    .displayName("파랑")
                    .status(OptionStatus.ACTIVE)
                    .sortOrder(2)
                    .build();

            UpdateOptionValueResult result = UpdateOptionValueResult.builder()
                    .id(valueId)
                    .value("BLUE")
                    .displayName("파랑")
                    .status(OptionStatus.ACTIVE)
                    .sortOrder(2)
                    .build();

            UpdateOptionValueResponse response = UpdateOptionValueResponse.builder()
                    .id(valueId)
                    .value("BLUE")
                    .displayName("파랑")
                    .status("ACTIVE")
                    .sortOrder(2)
                    .message("Option Value updated successfully")
                    .build();

            when(optionGroupApplicationService.updateOptionValue(eq(groupId), eq(valueId), any())).thenReturn(result);
            when(optionGroupResponseMapper.toUpdateOptionValueResponse(any())).thenReturn(response);

            mockMvc.perform(put("/option-groups/{groupId}/option-values/{valueId}", groupId, valueId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(valueId.toString()))
                    .andExpect(jsonPath("$.value").value("BLUE"));

            verify(optionGroupApplicationService).updateOptionValue(eq(groupId), eq(valueId), any());
            verify(optionGroupResponseMapper).toUpdateOptionValueResponse(any());
        }
    }

    @Nested
    @DisplayName("DELETE /option-groups/{groupId}/option-values/{valueId}")
    @WithMockUser(authorities = "ADMIN")
    class DeleteOptionValueTests {

        @Test
        @DisplayName("옵션 값 삭제 성공 시 200 OK")
        void deleteValue_Success() throws Exception {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();

            DeleteOptionValueResult result = DeleteOptionValueResult.builder()
                    .id(valueId)
                    .value("RED")
                    .build();

            DeleteOptionValueResponse response = DeleteOptionValueResponse.builder()
                    .id(valueId)
                    .value("RED")
                    .message("Option Value deleted successfully")
                    .build();

            when(optionGroupApplicationService.deleteOptionValue(groupId, valueId)).thenReturn(result);
            when(optionGroupResponseMapper.toDeleteOptionValueResponse(any())).thenReturn(response);

            mockMvc.perform(delete("/option-groups/{groupId}/option-values/{valueId}", groupId, valueId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(valueId.toString()))
                    .andExpect(jsonPath("$.message").value("Option Value deleted successfully"));

            verify(optionGroupApplicationService).deleteOptionValue(groupId, valueId);
            verify(optionGroupResponseMapper).toDeleteOptionValueResponse(any());
        }
    }
}
