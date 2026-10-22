package com.datn.financeapp.module.goal.service;

import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.module.goal.dto.request.GoalFilterRequest;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý mục tiêu tiết kiệm, nộp/rút tiền tích lũy và cập nhật tiến độ hoàn thành mục tiêu.
 */
public interface GoalService {

    /**
     * Tạo mới một mục tiêu tiết kiệm.
     *
     * @param userId ID người dùng
     * @param req    Yêu cầu tạo mục tiêu tiết kiệm
     * @return Kết quả tạo mục tiêu
     */
    CreateGoalResponse create(UUID userId, CreateGoalRequest req);

    /**
     * Nộp tiền vào mục tiêu tiết kiệm.
     *
     * @param userId ID người dùng
     * @param goalId ID mục tiêu tiết kiệm
     * @param req    Thông tin đóng góp
     * @return Kết quả nộp tiền thành công
     */
    CreateContributionResponse addContribution(UUID userId, UUID goalId, CreateContributionRequest req);

    /**
     * Hủy bỏ một đợt đóng góp tiết kiệm.
     *
     * @param userId         ID người dùng
     * @param goalId         ID mục tiêu tiết kiệm
     * @param contributionId ID lượt đóng góp cần hủy
     */
    void cancelContribution(UUID userId, UUID goalId, UUID contributionId);

    /**
     * Cập nhật thông tin mục tiêu tiết kiệm.
     *
     * @param userId ID người dùng
     * @param goalId ID mục tiêu tiết kiệm
     * @param req    Thông tin yêu cầu cập nhật
     * @return Mục tiêu sau khi cập nhật
     */
    GoalListItemResponse update(UUID userId, UUID goalId, UpdateGoalRequest req);

    /**
     * Xóa mục tiêu tiết kiệm (dành cho người dùng).
     *
     * @param userId             ID người dùng
     * @param goalId             ID mục tiêu tiết kiệm cần xóa
     * @param revertTransactions {@code true} nếu muốn hoàn tiền các đợt đóng góp về lại ví nguồn
     * @param deleteWallet       {@code true} nếu muốn xóa luôn ví SAVINGS liên kết của mục tiêu
     */
    void delete(UUID userId, UUID goalId, boolean revertTransactions, boolean deleteWallet);

    /**
     * Truy vấn danh sách các mục tiêu tiết kiệm của người dùng.
     *
     * @param userId ID người dùng
     * @param status Trạng thái mục tiêu ({@code in_progress}, {@code completed}, {@code cancelled})
     * @return Danh sách mục tiêu tiết kiệm
     */
    List<GoalListItemResponse> list(UUID userId, String status);

    /**
     * Lấy chi tiết mục tiêu tiết kiệm kèm lịch sử đóng góp.
     *
     * @param userId ID người dùng
     * @param goalId ID mục tiêu tiết kiệm
     * @return Chi tiết mục tiêu tiết kiệm
     */
    GoalDetailResponse detail(UUID userId, UUID goalId);

    // ---------------------------------------------------------------------
    // Phân hệ Quản trị (Admin)
    // ---------------------------------------------------------------------

    /**
     * Lấy danh sách mục tiêu tiết kiệm toàn hệ thống có phân trang và bộ lọc (Admin).
     *
     * @param filter   Bộ lọc tìm kiếm
     * @param pageable Thông tin phân trang
     * @return Trang danh sách mục tiêu
     */
    Page<GoalListItemResponse> adminList(GoalFilterRequest filter, Pageable pageable);

    /**
     * Lấy chi tiết mục tiêu của bất kỳ người dùng nào (Admin).
     *
     * @param goalId ID mục tiêu
     * @return Chi tiết mục tiêu kèm lịch sử đóng góp
     */
    GoalDetailResponse adminDetail(UUID goalId);

    /**
     * Xóa mục tiêu tiết kiệm (Admin).
     *
     * @param goalId             ID mục tiêu cần xóa
     * @param revertTransactions {@code true} nếu muốn hoàn tác giao dịch nạp tiền
     * @param deleteWallet       {@code true} nếu muốn xóa luôn ví SAVINGS liên kết
     */
    void adminDelete(UUID goalId, boolean revertTransactions, boolean deleteWallet);
}
