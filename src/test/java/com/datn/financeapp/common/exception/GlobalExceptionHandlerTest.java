package com.datn.financeapp.common.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra GlobalExceptionHandler qua @WebMvcTest với controller test-only,
 * không đụng tới controller nghiệp vụ thật nào.
 */
@WebMvcTest(
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.datn.financeapp.common.security.SecurityConfig.class,
                        com.datn.financeapp.common.security.JwtAuthFilter.class,
                        com.datn.financeapp.common.ratelimit.RateLimitFilter.class,
                        // Plan 04: AuthController mới thêm bị @WebMvcTest (không dùng controllers=)
                        // tự component-scan vào slice context, kéo theo AuthService không tồn tại
                        // ở đây — loại tường minh, cùng lý do với 3 bean phía trên.
                        com.datn.financeapp.auth.controller.AuthController.class,
                        // Phase 2 plan 02-02: WalletController mới thêm bị component-scan vào
                        // cùng slice context, kéo theo WalletService không tồn tại ở đây (chỉ
                        // GlobalExceptionHandlerTest + TestController được @Import tường minh) —
                        // cùng lý do loại trừ với AuthController phía trên.
                        com.datn.financeapp.wallet.controller.WalletController.class,
                        // Phase 2 plan 02-03: CategoryController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.category.controller.CategoryController.class,
                        // Phase 3 plan 03-02: TransactionController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.transaction.controller.TransactionController.class,
                        // Phase 4 plan 04-01: NotificationController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.notification.controller.NotificationController.class,
                        // Phase 4 plan 04-02: BudgetController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.budget.controller.BudgetController.class,
                        // Phase 4 plan 04-03: DebtController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.debt.controller.DebtController.class,
                        // Phase 4 plan 04-04: GoalController mới thêm — cùng lý do loại trừ.
                        com.datn.financeapp.goal.controller.GoalController.class
                }))
@org.springframework.context.annotation.Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@WithMockUser
public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationError_returnsAllFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.length()").value(2));
    }

    @Test
    void businessException_returnsCorrectCodeAndHttpStatus() throws Exception {
        mockMvc.perform(post("/test/business").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void runtimeException_doesNotLeakOriginalMessage() throws Exception {
        mockMvc.perform(post("/test/runtime").with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Đã có lỗi xảy ra, vui lòng thử lại sau."))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("chi-tiet-nhay-cam"))));
    }

    @RestController
    public static class TestController {

        @PostMapping("/test/validate")
        public void validate(@Valid @RequestBody TestDto dto) {
            // không tới đây nếu validate fail
        }

        @PostMapping("/test/business")
        public void business() {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", 409, "Email đã có người dùng.");
        }

        @PostMapping("/test/runtime")
        public void runtime() {
            throw new RuntimeException("chi-tiet-nhay-cam: loi ket noi database noi bo");
        }
    }

    public record TestDto(
            @JsonProperty("field_one") @NotBlank String fieldOne,
            @JsonProperty("field_two") @NotBlank String fieldTwo) {}
}
