package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProfileService userProfileService;

    @Mock
    private AccountService userAccountService;

    @InjectMocks
    private UserController userController;

    private final UUID currentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users/me: Lấy thông tin hồ sơ của user hiện tại -> 200 OK")
    void getMe_Success_ReturnsUserProfile() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                currentUserId, "test@example.com", "Nam", "Nguyen", "avatar.png", UserPlan.FREE
        );
        when(userProfileService.getMe(currentUserId)).thenReturn(response);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(currentUserId.toString()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Nam"));
    }

    @Test
    @DisplayName("PATCH /users/me: Cập nhật thông tin hồ sơ -> 200 OK")
    void updateProfile_Success_ReturnsUpdatedProfile() throws Exception {
        UpdateMeRequest req = new UpdateMeRequest("Minh", "Tran", "new-avatar.png");
        UserProfileResponse updated = new UserProfileResponse(
                currentUserId, "test@example.com", "Minh", "Tran", "new-avatar.png", UserPlan.FREE
        );
        when(userProfileService.updateMe(eq(currentUserId), any(UpdateMeRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Minh"))
                .andExpect(jsonPath("$.data.lastName").value("Tran"));
    }

    @Test
    @DisplayName("PUT /users/me/password: Đổi mật khẩu thành công -> 200 OK")
    void changePassword_Success_Returns200() throws Exception {
        UpdatePassReq req = new UpdatePassReq("oldPassword123", "newPassword456");
        doNothing().when(userAccountService).changePassword(eq(currentUserId), any(UpdatePassReq.class));

        mockMvc.perform(put("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userAccountService).changePassword(eq(currentUserId), any(UpdatePassReq.class));
    }

    @Test
    @DisplayName("DELETE /users/me: Xóa tài khoản thành công -> 200 OK")
    void deleteAccount_Success_Returns200() throws Exception {
        DeleteAccountRequest req = new DeleteAccountRequest("myPassword123");
        doNothing().when(userProfileService).deleteMe("myPassword123");

        mockMvc.perform(delete("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userProfileService).deleteMe("myPassword123");
    }
}
