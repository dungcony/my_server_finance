package com.datn.financeapp.module.transaction.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Kiểm tra nghiệp vụ của giao dịch theo loại, ném {@link BusinessException} khi vi phạm.
 * <ul>
 *   <li>{@link #type(TransactionType, Long, UUID, LocalDate)}: điểm vào duy nhất, chọn bộ luật theo loại giao dịch.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionValidator {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CategoryService categoryService;

    /**
     * Kiểm tra một giao dịch theo loại của nó.
     *
     * @param type       loại giao dịch
     * @param amount     số tiền giao dịch
     * @param categoryId ID danh mục (chi/thu bắt buộc, cho vay không kiểm)
     * @param date       ngày giao dịch, {@code null} nghĩa là lấy hôm nay nên không kiểm
     * @throws BusinessException nếu vi phạm luật của loại giao dịch đó
     */
    public void type(TransactionType type, Long amount, UUID categoryId, LocalDate date) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);
        }
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);
        }
        CategoryResponse category = categoryService.findActiveById(categoryId);
        if (!Boolean.TRUE.equals(category.isLeaf())) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF);
        }
    }
}
