package com.datn.financeapp.user;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.user.controller.ManagerUserController;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.service.ManagerAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ManagerAccountService adminUserService;

    @InjectMocks
    private ManagerUserController adminUserController;

    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("TC_CTL_01: Admin block user thành công -> 200 OK với msg và success=true")
    void blockUser_Success_Returns200Ok() throws Exception {
        BlockUserRequest req = new BlockUserRequest(targetUserId, "Vi phạm chính sách cộng đồng");
        doNothing().when(adminUserService).blockUser(req);

        mockMvc.perform(patch("/admin/user/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Khóa tài khoản người dùng thành công"));
    }

    @Test
    @DisplayName("TC_CTL_02: Request body có userId null -> 400 VALIDATION_ERROR")
    void blockUser_NullUserId_Returns400ValidationError() throws Exception {
        BlockUserRequest req = new BlockUserRequest(null, "Lý do");

        mockMvc.perform(patch("/admin/user/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TC_CTL_03: Admin tự khóa chính mình -> 400 VALIDATION_ERROR từ Service")
    void blockUser_SelfBlock_Returns400ValidationError() throws Exception {
        BlockUserRequest req = new BlockUserRequest(targetUserId, "Tự khóa");
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "Không được phép tự khóa tài khoản của chính mình."))
                .when(adminUserService).blockUser(req);

        mockMvc.perform(patch("/admin/user/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Không được phép tự khóa tài khoản của chính mình."));
    }

    @Test
    @DisplayName("TC_CTL_04: Target user không tồn tại hoặc đã xóa mềm -> 404 NOT_FOUND")
    void blockUser_NotFound_Returns404NotFound() throws Exception {
        BlockUserRequest req = new BlockUserRequest(targetUserId, "Lý do");
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng."))
                .when(adminUserService).blockUser(req);

        mockMvc.perform(patch("/admin/user/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Không tìm thấy người dùng."));
    }

    @Test
    @DisplayName("TC_CTL_05: Admin gán role cho user thành công -> 200 OK với msg và success=true")
    void addRoleToUser_Success_Returns200Ok() throws Exception {
        UpdateUserRoleReq req = new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER);
        doNothing().when(adminUserService).addRoleToUser(req);

        mockMvc.perform(post("/admin/user/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Gán vai trò cho người dùng thành công"));
    }
}
