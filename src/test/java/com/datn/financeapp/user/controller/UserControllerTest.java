package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.request.UpdateProfileRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.exception.PasswordAlreadySetException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                currentUserId, "test@example.com", "Nam", "Nguyen", "avatar.png", UserPlan.FREE, true
        );
        when(userProfileService.getMe(currentUserId)).thenReturn(response);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(currentUserId.toString()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Nam"))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    @DisplayName("POST /users/me/password: Không body, chỉ cần token -> gọi sinh mật khẩu cho user hiện tại -> 200 OK")
    void generatePassword_Success_Returns200() throws Exception {
        doNothing().when(userAccountService).generatePassword(currentUserId);

        mockMvc.perform(post("/users/me/password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Mật khẩu đã được gửi về email của bạn."));

        verify(userAccountService).generatePassword(currentUserId);
    }

    @Test
    @DisplayName("POST /users/me/password: Tài khoản đã có mật khẩu -> 409 PASSWORD_ALREADY_SET")
    void generatePassword_AlreadyHasPassword_Returns409() throws Exception {
        doThrow(new PasswordAlreadySetException()).when(userAccountService).generatePassword(currentUserId);

        mockMvc.perform(post("/users/me/password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_ALREADY_SET"));
    }

    @Test
    @DisplayName("PATCH /users/me: Cập nhật thông tin hồ sơ -> 200 OK")
    void updateProfile_Success_ReturnsUpdatedProfile() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest("Minh", "Tran", "new-avatar.png");
        UserProfileResponse updated = new UserProfileResponse(
                currentUserId, "test@example.com", "Minh", "Tran", "new-avatar.png", UserPlan.FREE, true
        );
        when(userProfileService.updateMe(eq(currentUserId), any(UpdateProfileRequest.class))).thenReturn(updated);

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
