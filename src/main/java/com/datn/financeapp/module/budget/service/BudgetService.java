package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.dto.response.BudgetRes;
import com.datn.financeapp.module.budget.enums.PeriodType;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý ngân sách: hạn mức chi theo danh mục trong một kỳ.
 * <p>
 * <b>Các hàm:</b>
 * <ul>
 *   <li>{@link #list(UUID, Boolean, PeriodType)}: danh sách ngân sách của người dùng, lọc theo hiệu lực/kỳ.</li>
 *   <li>{@link #detail(UUID, UUID)}: chi tiết một ngân sách.</li>
 *   <li>{@link #create(UUID, CreateBudgetRequest)}: tạo ngân sách, tính sẵn số đã chi của kỳ.</li>
 *   <li>{@link #update(UUID, UUID, UpdateBudgetRequest)}: sửa hạn mức, tự gia hạn, bật/tắt.</li>
 *   <li>{@link #delete(UUID, UUID)}: vô hiệu hoá (xoá mềm) ngân sách.</li>
 * </ul>
 * Việc cập nhật số đã chi, tổng quan/cảnh báo và gia hạn kỳ nằm ở {@link BudgetSpendService},
 * {@link BudgetOverviewService}, {@link BudgetRenewalService}.
 * </p>
 */
public interface BudgetService {

    /**
     * Danh sách ngân sách của người dùng, kỳ bắt đầu gần nhất trước.
     *
     * @param userId     ID người dùng
     * @param isActive   chỉ lấy theo trạng thái hiệu lực; {@code null} là lấy hết
     * @param periodType chỉ lấy theo loại kỳ; {@code null} là lấy hết
     * @return danh sách ngân sách kèm tiến độ
     */
    List<BudgetRes> list(UUID userId, Boolean isActive, PeriodType periodType);

    /**
     * Chi tiết một ngân sách của người dùng.
     *
     * @param userId   ID người dùng sở hữu
     * @param budgetId ID ngân sách
     * @return ngân sách kèm tiến độ
     * @throws BusinessException {@code NOT_FOUND} nếu không có hoặc không thuộc về người dùng
     */
    BudgetRes detail(UUID userId, UUID budgetId);

    /**
     * Tạo ngân sách mới. Số đã chi của kỳ được tính sẵn từ các giao dịch chi đã có (gồm danh mục con),
     * vì ngân sách thường được tạo giữa kỳ.
     *
     * @param userId ID người dùng
     * @param req    thông tin ngân sách
     * @return ngân sách vừa tạo
     * @throws BusinessException {@code CATEGORY_NOT_FOUND} nếu danh mục không có; {@code BUDGET_ALREADY_EXISTS} nếu
     *                           trùng ngân sách cùng danh mục trong kỳ chồng lấn
     */
    BudgetRes create(UUID userId, CreateBudgetRequest req);

    /**
     * Sửa hạn mức, tự gia hạn hoặc trạng thái hiệu lực. Danh mục và loại kỳ không sửa được.
     *
     * @param userId   ID người dùng sở hữu
     * @param budgetId ID ngân sách
     * @param req      thông tin cần sửa
     * @return ngân sách sau khi sửa
     * @throws BusinessException {@code NOT_FOUND}; {@code BUDGET_CATEGORY_NOT_EDITABLE} nếu đổi danh mục/kỳ;
     *                           {@code BUDGET_ALREADY_EXISTS} nếu bật lại mà trùng kỳ
     */
    BudgetRes update(UUID userId, UUID budgetId, UpdateBudgetRequest req);

    /**
     * Vô hiệu hoá ngân sách (xoá mềm), không ảnh hưởng giao dịch.
     *
     * @param userId   ID người dùng sở hữu
     * @param budgetId ID ngân sách
     * @throws BusinessException {@code NOT_FOUND} nếu không có hoặc không thuộc về người dùng
     */
    void delete(UUID userId, UUID budgetId);
}
