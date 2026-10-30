package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.module.budget.dto.response.BudgetAlertRes;
import com.datn.financeapp.module.budget.dto.response.BudgetSummaryRes;

import java.util.List;
import java.util.UUID;

/**
 * Các góc nhìn chỉ đọc trên những ngân sách đang hiệu lực trong kỳ hiện tại.
 * <p>
 * <b>Các hàm:</b>
 * <ul>
 *   <li>{@link #summary(UUID)}: tổng hạn mức, đã chi, còn lại và số ngân sách theo trạng thái.</li>
 *   <li>{@link #alerts(UUID)}: ngân sách đã từ 80% hạn mức, nặng nhất trước.</li>
 * </ul>
 * Module báo cáo dùng {@link #alerts(UUID)} để dựng mục "ngân sách cần chú ý".
 * </p>
 */
public interface BudgetOverviewService {

    /**
     * Tổng quan các ngân sách đang hiệu lực mà hôm nay nằm trong kỳ.
     *
     * @param userId ID người dùng
     * @return tổng hạn mức, đã chi, còn lại và số ngân sách theo trạng thái
     */
    BudgetSummaryRes summary(UUID userId);

    /**
     * Cảnh báo cho các ngân sách đang hiệu lực đã từ 80% hạn mức, tỉ lệ cao nhất trước.
     *
     * @param userId ID người dùng
     * @return danh sách cảnh báo
     */
    List<BudgetAlertRes> alerts(UUID userId);
}
