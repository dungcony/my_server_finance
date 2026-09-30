package com.datn.financeapp.group.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.group.dto.request.group.GroupMemberRoleReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.service.MemberService;
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
    private GroupService groupService;

    @Mock
    private MemberService memberService;

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
    @DisplayName("GET /groups/{id}/members - Lấy danh sách thành viên nhóm")
    void listMembers_success() throws Exception {
        UUID memberId = UUID.randomUUID();
        MemberRes member = new MemberRes(
                memberId, userId, MemberRole.OWNER, MemberStatus.ACTIVE, Instant.now()
        );
        when(memberService.findMembers(groupId)).thenReturn(List.of(member));

        mockMvc.perform(get("/groups/{id}/members", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(memberId.toString()))
                .andExpect(jsonPath("$.data[0].user_id").value(userId.toString()));

        verify(memberService).findMembers(groupId);
    }

    @Test
    @DisplayName("PATCH /groups/{id}/members/{memberUserId}/role - Chuyển quyền owner khi role là OWNER")
    void updateMemberRole_transferOwner() throws Exception {
        UUID targetMemberId = UUID.randomUUID();
        GroupMemberRoleReq req = new GroupMemberRoleReq(MemberRole.OWNER);

        mockMvc.perform(patch("/groups/{id}/members/{memberUserId}/role", groupId, targetMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupService).transferOwnership(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{id}/members/{memberUserId}/approve - Duyệt thành viên")
    void approveMember_success() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(post("/groups/{id}/members/{memberUserId}/approve", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberService).approve(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("DELETE /groups/{id}/members/{memberUserId} - Xóa thành viên khỏi nhóm")
    void removeMember_success() throws Exception {
        UUID targetMemberId = UUID.randomUUID();

        mockMvc.perform(delete("/groups/{id}/members/{memberUserId}", groupId, targetMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberService).removeMember(userId, groupId, targetMemberId);
    }

    @Test
    @DisplayName("POST /groups/{id}/leave - Rời khỏi nhóm")
    void leaveGroup_success() throws Exception {
        mockMvc.perform(post("/groups/{id}/leave", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(memberService).leave(userId, groupId);
    }
}
