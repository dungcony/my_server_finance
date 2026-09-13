package com.datn.financeapp.user;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.user.controller.ManagerRoleController;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.service.ManagerRoleService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ManagerRoleService adminRoleService;

    @InjectMocks
    private ManagerRoleController adminRoleController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminRoleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("TC_ROLE_CTL_01: Gán permission cho role thành công -> 200 OK")
    void addPermissionToRole_Success_Returns200Ok() throws Exception {
        AddPermissionRoleRequest req = new AddPermissionRoleRequest(RoleName.ROLE_ADMIN, PermissionName.USERS_READ);
        doNothing().when(adminRoleService).addPermissionToRole(req);

        mockMvc.perform(post("/admin/role/permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Gán quyền hạn cho vai trò thành công"));
    }

    @Test
    @DisplayName("TC_ROLE_CTL_02: Lấy danh sách tất cả vai trò -> 200 OK")
    void findRoles_Success_Returns200Ok() throws Exception {
        when(adminRoleService.findRoles()).thenReturn(List.of());

        mockMvc.perform(get("/admin/role/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC_ROLE_CTL_03: Lấy danh sách quyền hạn theo vai trò -> 200 OK")
    void findByRole_Success_Returns200Ok() throws Exception {
        when(adminRoleService.findByRole(RoleName.ROLE_ADMIN)).thenReturn(List.of());

        mockMvc.perform(get("/admin/role/ROLE_ADMIN/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
