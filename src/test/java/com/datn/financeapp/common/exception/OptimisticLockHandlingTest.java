package com.datn.financeapp.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiểm thử {@link GlobalExceptionHandler} với lỗi khoá lạc quan: hai request cùng ghi một bản ghi
 * thì request đến sau phải nhận 409, không phải 500.
 * <p>
 * Dựng MockMvc độc lập (không nạp Spring context) nên không phụ thuộc cấu hình bảo mật hay giới hạn tốc độ.
 */
class OptimisticLockHandlingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Ghi đè bản ghi đã bị người khác sửa trước thì trả 409 CONCURRENT_MODIFICATION thay vì 500")
    void optimisticLockFailure_returnsConflictInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
        }
    }
}
