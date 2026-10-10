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
        switch (type) {
            case EXPENSE -> validExpense(amount, categoryId, date);
            case INCOME -> validIncome(amount, categoryId, date);
            case LOAN -> validLoan(amount, date);
        }
    }

    //-------------------------PRIVATE------------------------//

    /**
     * Khoản chi: số tiền dương, ngày không ở tương lai, có danh mục lá.
     */
    private void validExpense(Long amount, UUID categoryId, LocalDate date) {
        validCategorized(amount, categoryId, date);
    }

    /**
     * Khoản thu: số tiền dương, ngày không ở tương lai, có danh mục lá.
     */
    private void validIncome(Long amount, UUID categoryId, LocalDate date) {
        validCategorized(amount, categoryId, date);
    }

    /**
     * Khoản cho vay: số tiền dương, ngày không ở tương lai. Không kiểm danh mục.
     */
    private void validLoan(Long amount, LocalDate date) {
        validAmount(amount);
        validDateNotFuture(date);
    }

    // Luật chung của khoản chi và khoản thu; kiểm danh mục để sau cùng vì phải tra CSDL.
    private void validCategorized(Long amount, UUID categoryId, LocalDate date) {
        validAmount(amount);
        validDateNotFuture(date);
        validCategory(categoryId);
    }

    private void validAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);
        }
    }

    private void validDateNotFuture(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now(VIETNAM_ZONE))) {
            throw new BusinessException(ErrorCode.TRANSACTION_DATE_IN_FUTURE);
        }
    }

    // findActiveById tự ném CATEGORY_NOT_FOUND khi danh mục không có hoặc đã xóa mềm.
    private void validCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);
        }
        CategoryResponse category = categoryService.findActiveById(categoryId);
        if (!Boolean.TRUE.equals(category.isLeaf())) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF);
        }
    }
}
