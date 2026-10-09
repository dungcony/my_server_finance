package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.dto.response.BudgetAlertResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetImpactResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetListItemResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetSuggestionResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetSummaryResponse;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.report.dto.response.ReportHomeResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service điều phối quy trình quản lý ngân sách chi tiêu, tính toán tiến độ, cảnh báo vượt hạn mức và tự động gia hạn kỳ.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #list(UUID, Boolean, String)}: Lấy danh sách ngân sách chi tiêu của người dùng.</li>
 *   <li>{@link #detail(UUID, UUID)}: Lấy thông tin chi tiết một ngân sách theo ID.</li>
 *   <li>{@link #summary(UUID)}: Tổng hợp thông tin ngân sách đang hiệu lực.</li>
 *   <li>{@link #alerts(UUID)}: Lấy danh sách cảnh báo ngân sách vượt hoặc sắp vượt hạn mức.</li>
 *   <li>{@link #attentionItems(UUID)}: Lấy danh sách ngân sách cần chú ý cho trang tổng quan báo cáo.</li>
 *   <li>{@link #suggestion(UUID, UUID)}: Gợi ý hạn mức ngân sách dựa trên lịch sử chi tiêu.</li>
 *   <li>{@link #create(UUID, CreateBudgetRequest)}: Tạo mới một ngân sách chi tiêu.</li>
 *   <li>{@link #update(UUID, UUID, UpdateBudgetRequest)}: Cập nhật hạn mức hoặc thuộc tính của ngân sách.</li>
 *   <li>{@link #delete(UUID, UUID)}: Vô hiệu hóa (xóa mềm) ngân sách.</li>
 *   <li>{@link #renewExpiredBudgets()}: Tự động gia hạn các ngân sách đã hết kỳ.</li>
 *   <li>{@link #findImpactedBudgets(UUID, String, UUID, LocalDate)}: Tìm các ngân sách bị ảnh hưởng bởi khoản chi tiêu mới.</li>
 * </ul>
 * </p>
 */
public interface BudgetService {

    /**
     * Truy vấn danh sách ngân sách của người dùng theo bộ lọc.
     *
     * @param userId     ID của người dùng thực hiện truy vấn
     * @param isActive   Trạng thái hiệu lực của ngân sách ({@code true}: đang hoạt động, {@code false}: đã tắt)
     * @param periodType Loại kỳ ngân sách (VD: {@code week}, {@code month}, {@code quarter}, {@code year})
     * @return Danh sách các ngân sách thỏa mãn điều kiện
     */
    List<BudgetListItemResponse> list(UUID userId, Boolean isActive, String periodType);

    /**
     * Lấy chi tiết thông tin và tiến độ của một ngân sách.
     *
     * @param userId   ID của người dùng sở hữu
     * @param budgetId ID của ngân sách cần lấy chi tiết
     * @return Thông tin chi tiết ngân sách
     * @throws BusinessException Nếu không tìm thấy ngân sách với ID tương ứng
     */
    BudgetListItemResponse detail(UUID userId, UUID budgetId);

    /**
     * Lấy dữ liệu tổng hợp về toàn bộ ngân sách đang hiệu lực của người dùng.
     *
     * @param userId ID của người dùng
     * @return Dữ liệu tổng hợp ngân sách
     */
    BudgetSummaryResponse summary(UUID userId);

    /**
     * Lấy danh sách cảnh báo dành cho các ngân sách sắp vượt hoặc đã vượt hạn mức.
     *
     * @param userId ID của người dùng
     * @return Danh sách cảnh báo ngân sách
     */
    List<BudgetAlertResponse> alerts(UUID userId);

    /**
     * Lấy danh sách các ngân sách cần chú ý hiển thị trên trang chủ báo cáo.
     *
     * @param userId ID của người dùng
     * @return Danh sách mục ngân sách cần chú ý
     */
    List<ReportHomeResponse.BudgetAttentionItem> attentionItems(UUID userId);

    /**
     * Đề xuất hạn mức ngân sách cho một danh mục dựa trên trung bình chi tiêu của các kỳ trước.
     *
     * @param userId     ID của người dùng
     * @param categoryId ID danh mục cần xin gợi ý hạn mức
     * @return Dữ liệu gợi ý hạn mức ngân sách
     * @throws BusinessException Nếu không tìm thấy danh mục
     */
    BudgetSuggestionResponse suggestion(UUID userId, UUID categoryId);

    /**
     * Tạo mới một ngân sách chi tiêu.
     *
     * @param userId ID của người dùng tạo ngân sách
     * @param req    Thông tin yêu cầu tạo ngân sách
     * @return Thông tin ngân sách vừa tạo thành công
     * @throws BusinessException Nếu danh mục không hợp lệ, không phải danh mục chi tiêu, hoặc trùng ngân sách
     */
    BudgetListItemResponse create(UUID userId, CreateBudgetRequest req);

    /**
     * Cập nhật thông tin ngân sách chi tiêu hiện có.
     *
     * @param userId   ID của người dùng sở hữu
     * @param budgetId ID của ngân sách cần cập nhật
     * @param req      Thông tin yêu cầu cập nhật
     * @return Thông tin ngân sách sau khi cập nhật
     * @throws BusinessException Nếu không tìm thấy ngân sách hoặc cố tình sửa danh mục/kỳ ngân sách
     */
    BudgetListItemResponse update(UUID userId, UUID budgetId, UpdateBudgetRequest req);

    /**
     * Vô hiệu hóa (xóa mềm) ngân sách chi tiêu.
     *
     * @param userId   ID của người dùng sở hữu
     * @param budgetId ID của ngân sách cần xóa
     * @throws BusinessException Nếu không tìm thấy ngân sách
     */
    void delete(UUID userId, UUID budgetId);

    /**
     * Quét và thực hiện gia hạn tự động cho tất cả các ngân sách đã hết hạn kỳ hiện tại.
     */
    void renewExpiredBudgets();

    /**
     * Tìm danh sách các ngân sách bị tác động bởi một khoản chi tiêu vừa phát sinh.
     *
     * @param userId     ID của người dùng
     * @param type       Loại giao dịch (VD: {@code expense})
     * @param categoryId ID danh mục của giao dịch
     * @param date       Ngày phát sinh giao dịch
     * @return Danh sách các ngân sách bị ảnh hưởng kèm thông điệp cảnh báo
     */
    List<BudgetImpactResponse> findImpactedBudgets(
            UUID userId, String type, UUID categoryId, LocalDate date);

    /**
     * Định dạng số tiền thành chuỗi tiền tệ hiển thị (Đơn vị: VND).
     *
     * @param amount Số tiền dạng nguyên
     * @return Chuỗi định dạng số tiền (VD: "500.000 ₫")
     */
    static String formatAmount(long amount) {
        return String.format("%,d ₫", amount).replace(",", ".");
    }

    /**
     * Tính toán ngày kết thúc kỳ ngân sách dựa trên loại kỳ và ngày bắt đầu.
     *
     * @param periodType Loại kỳ ngân sách ({@code week}, {@code month}, {@code quarter}, {@code year})
     * @param startDate  Ngày bắt đầu kỳ
     * @return Ngày kết thúc kỳ ngân sách
     * @throws BusinessException Nếu loại kỳ không hợp lệ
     */
    static LocalDate endOfPeriod(String periodType, LocalDate startDate) {
        return switch (periodType) {
            case "week" -> startDate.plusDays(6);
            case "month" -> startDate.plusMonths(1).minusDays(1);
            case "quarter" -> startDate.plusMonths(3).minusDays(1);
            case "year" -> startDate.plusYears(1).minusDays(1);
            default -> throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "Loại kỳ ngân sách không hợp lệ.");
        };
    }
}
