package com.datn.financeapp.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.service.MemberBehavierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private MemberBehavierService memberBehavierService;

    @InjectMocks
    private MemberController memberController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(memberController)
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
    @DisplayName("POST /groups/{groupId}/members - Thêm thành viên vào nhóm")
    void addMembers_success() throws Exception {
        UUID newUserId = UUID.randomUUID();
        MemberAddReq req = new MemberAddReq(List.of(newUserId));

        when(memberBehavierService.ownerAddMembers(eq(userId), eq(groupId), any(MemberAddReq.class)))
                .thenReturn(List.of());

        mockMvc.perform(post("/groups/{groupId}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).ownerAddMembers(eq(userId), eq(groupId), any(MemberAddReq.class));
    }

    @Test
    @DisplayName("PUT /groups/{id}/owner-role/{memberUserId}/ - Chuyển quyền owner")
    void updateMemberRole_transferOwner() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(put("/groups/{id}/owner-role/{memberUserId}/", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).transferOwnership(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{id}/members/{memberUserId}/approve - Duyệt thành viên")
    void approveMember_success() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(post("/groups/{id}/members/{memberUserId}/approve", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).approve(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/approves - Duyệt tất cả thành viên chờ")
    void approveMembers_success() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/approves", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).approveAll(userId, groupId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/members/{memberUserId}/reject - Từ chối thành viên")
    void rejectMember_success() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(post("/groups/{groupId}/members/{memberUserId}/reject", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).reject(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/rejects - Từ chối tất cả thành viên chờ")
    void rejectMembers_success() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/rejects", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).rejectAll(userId, groupId);
    }

    @Test
    @DisplayName("DELETE /groups/{id}/members/{memberUserId} - Xóa thành viên khỏi nhóm")
    void removeMember_success() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(delete("/groups/{id}/members/{memberUserId}", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).removeMember(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{id}/leave - Rời khỏi nhóm")
    void leaveGroup_success() throws Exception {
        mockMvc.perform(post("/groups/{id}/leave", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberBehavierService).leave(userId, groupId);
    }
}
