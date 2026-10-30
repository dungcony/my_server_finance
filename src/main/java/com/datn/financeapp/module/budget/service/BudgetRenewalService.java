package com.datn.financeapp.module.budget.service;

/**
 * Tự gia hạn ngân sách: khi một ngân sách bật tự gia hạn đã hết kỳ thì tạo kỳ mới nối tiếp.
 * <p>
 * <b>Các hàm:</b>
 * <ul>
 *   <li>{@link #renewExpiredBudgets()}: quét và gia hạn mọi ngân sách đã hết kỳ.</li>
 * </ul>
 * </p>
 */
public interface BudgetRenewalService {

    /**
     * Quét ngân sách đang hiệu lực, bật tự gia hạn và đã hết kỳ; tạo kỳ mới nối tiếp, tắt kỳ cũ và gửi
     * thông báo. Mỗi ngân sách chạy trong transaction riêng nên một cái lỗi không kéo theo cái khác.
     */
    void renewExpiredBudgets();
}
