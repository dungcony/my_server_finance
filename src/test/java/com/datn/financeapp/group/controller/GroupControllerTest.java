package com.datn.financeapp.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private GroupService groupService;

    @InjectMocks
    private GroupController groupController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(groupController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /groups - Tạo nhóm thành công trả về 201")
    void createGroup_success() throws Exception {
        GroupCreateReq req = new GroupCreateReq("Nhóm Gia Đình", "Mô tả", 1000000L, true, true, null);
        GroupDetailRes res = new GroupDetailRes(
                groupId, "Nhóm Gia Đình", "Mô tả", GroupStatus.ACTIVE,
                "GRP12345", 1000000L, true, true, Instant.now(), MemberRole.OWNER, null, Collections.emptyList()
        );

        when(groupService.create(eq(userId), any(GroupCreateReq.class))).thenReturn(res);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nhóm Gia Đình"));

        verify(groupService).create(eq(userId), any(GroupCreateReq.class));
    }

    @Test
    @DisplayName("GET /groups - Lấy danh sách nhóm thành công")
    void listGroups_success() throws Exception {
        GroupSummaryRes summary = new GroupSummaryRes(
                groupId, "Nhóm Gia Đình", MemberRole.OWNER, GroupStatus.ACTIVE,
                "GRP12345", 3L, 500000L, 1000000L, Instant.now()
        );
        when(groupService.list(userId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(groupId.toString()));

        verify(groupService).list(userId);
    }

    @Test
    @DisplayName("GET /groups/{id} - Xem chi tiết nhóm thành công")
    void detailGroup_success() throws Exception {
        GroupDetailRes res = new GroupDetailRes(
                groupId, "Nhóm Gia Đình", "Mô tả", GroupStatus.ACTIVE,
                "GRP12345", 1000000L, true, true, Instant.now(), MemberRole.OWNER, null, Collections.emptyList()
        );
        when(groupService.detail(userId, groupId)).thenReturn(res);

        mockMvc.perform(get("/groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(groupId.toString()));

        verify(groupService).detail(userId, groupId);
    }

    @Test
    @DisplayName("PATCH /groups/{id} - Cập nhật thông tin nhóm thành công")
    void updateGroup_success() throws Exception {
        GroupUpdateReq req = new GroupUpdateReq("Nhóm Mới", "Mô tả mới", 2000000L, true, true);
        GroupDetailRes res = new GroupDetailRes(
                groupId, "Nhóm Mới", "Mô tả mới", GroupStatus.ACTIVE,
                "GRP12345", 2000000L, true, true, Instant.now(), MemberRole.OWNER, null, Collections.emptyList()
        );
        when(groupService.update(eq(userId), eq(groupId), any(GroupUpdateReq.class))).thenReturn(res);

        mockMvc.perform(patch("/groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nhóm Mới"));

        verify(groupService).update(eq(userId), eq(groupId), any(GroupUpdateReq.class));
    }

    @Test
    @DisplayName("DELETE /groups/{id} - Xóa nhóm thành công")
    void deleteGroup_success() throws Exception {
        mockMvc.perform(delete("/groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupService).delete(userId, groupId);
    }

    @Test
    @DisplayName("POST /groups/join - Tham gia nhóm thành công")
    void joinGroup_success() throws Exception {
        GroupJoinReq req = new GroupJoinReq("GRP12345");

        mockMvc.perform(post("/groups/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupService).joinByCode(userId, req);
    }

    @Test
    @DisplayName("POST /groups/{id}/archive - Lưu trữ nhóm")
    void archiveGroup_success() throws Exception {
        mockMvc.perform(post("/groups/{id}/archive", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupService).archive(userId, groupId);
    }

    @Test
    @DisplayName("POST /groups/{id}/unarchive - Hủy lưu trữ nhóm")
    void unarchiveGroup_success() throws Exception {
        mockMvc.perform(post("/groups/{id}/unarchive", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupService).unarchive(userId, groupId);
    }
}
