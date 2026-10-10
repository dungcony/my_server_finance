package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Giữ số đã chi ({@code spent}) của ngân sách khớp với giao dịch chi. Phải gọi trong CÙNG transaction với
 * việc ghi giao dịch, nếu không số sẽ lệch âm thầm.
 * <p>
 * Ngân sách đặt ở danh mục cha cộng cả giao dịch ghi ở danh mục con. Sửa một giao dịch = {@link #removeExpense}
 * khoản cũ rồi {@link #addExpense} khoản mới.
 * </p>
 * <p>
 * <b>Các hàm:</b>
 * <ul>
 *   <li>{@link #addExpense(UUID, UUID, LocalDate, long)}: cộng một khoản chi, ghi cảnh báo nếu ngân sách chạm ngưỡng.</li>
 *   <li>{@link #removeExpense(UUID, UUID, LocalDate, long)}: trừ lại một khoản chi (xoá hoặc hoàn tác phần cũ khi sửa).</li>
 * </ul>
 * </p>
 */
public interface BudgetSpendService {

    /**
     * Cộng một khoản chi vào mọi ngân sách đang hiệu lực mà nó rơi vào. Ngân sách chạm 80% hoặc 100%
     * thì ghi thông báo cảnh báo (mỗi ngày tối đa một lần cho mỗi ngân sách).
     *
     * @param userId     ID người dùng sở hữu giao dịch
     * @param categoryId ID danh mục của giao dịch; {@code null} thì không làm gì
     * @param date       ngày giao dịch
     * @param amount     số tiền chi, dương
     * @return các ngân sách bị ảnh hưởng kèm số đã chi mới; rỗng nếu không có
     */
    List<BudgetAffectedRes> addExpense(UUID userId, UUID categoryId, LocalDate date, long amount);

    /**
     * Trừ lại một khoản chi khỏi mọi ngân sách đang hiệu lực mà nó rơi vào. Số đã chi không xuống dưới 0
     * và việc này không bao giờ sinh cảnh báo.
     *
     * @param userId     ID người dùng sở hữu giao dịch
     * @param categoryId ID danh mục của giao dịch; {@code null} thì không làm gì
     * @param date       ngày giao dịch
     * @param amount     số tiền chi cần trừ, dương
     */
    void removeExpense(UUID userId, UUID categoryId, LocalDate date, long amount);
}
