package com.datn.financeapp.common.exception;

import com.datn.financeapp.user.controller.ManagerRoleController;
import com.datn.financeapp.user.controller.ManagerUserController;
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
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
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
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_ALREADY_EXISTS"));
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
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }

        @PostMapping("/test/runtime")
        public void runtime() {
            throw new RuntimeException("chi-tiet-nhay-cam: loi ket noi database noi bo");
        }
    }

    public record TestDto(
            @JsonProperty("field_one") @NotBlank String fieldOne,
            @JsonProperty("field_two") @NotBlank String fieldTwo) {
    }
}

