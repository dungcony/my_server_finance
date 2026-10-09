package com.datn.financeapp.module.goal.service;

import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý mục tiêu tiết kiệm, nộp/rút tiền tích lũy và cập nhật tiến độ hoàn thành mục tiêu.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #create(UUID, CreateGoalRequest)}: Tạo mới mục tiêu tiết kiệm.</li>
 *   <li>{@link #addContribution(UUID, UUID, CreateContributionRequest)}: Nộp tiền hoặc rút tiền khỏi mục tiêu.</li>
 *   <li>{@link #cancelContribution(UUID, UUID, UUID)}: Hủy bỏ đợt đóng góp tiết kiệm.</li>
 *   <li>{@link #update(UUID, UUID, UpdateGoalRequest)}: Cập nhật thông tin mục tiêu tiết kiệm.</li>
 *   <li>{@link #delete(UUID, UUID, boolean)}: Xóa mục tiêu tiết kiệm.</li>
 *   <li>{@link #list(UUID, String)}: Truy vấn danh sách các mục tiêu tiết kiệm.</li>
 *   <li>{@link #detail(UUID, UUID)}: Lấy chi tiết mục tiêu tiết kiệm kèm lịch sử đóng góp.</li>
 * </ul>
 * </p>
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
     * Nộp tiền vào hoặc rút tiền khỏi mục tiêu tiết kiệm.
     *
     * @param userId ID người dùng
     * @param goalId ID mục tiêu tiết kiệm
     * @param req    Thông tin đóng góp/rút tiền
     * @return Kết quả nộp/rút tiền thành công
     */
    CreateContributionResponse addContribution(UUID userId, UUID goalId, CreateContributionRequest req);

    /**
     * Hủy bỏ một đợt đóng góp/rút tiền tiết kiệm.
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
     * Xóa mục tiêu tiết kiệm.
     *
     * @param userId             ID người dùng
     * @param goalId             ID mục tiêu tiết kiệm cần xóa
     * @param revertTransactions {@code true} nếu muốn hoàn tiền các đợt đóng góp về lại ví
     */
    void delete(UUID userId, UUID goalId, boolean revertTransactions);

    /**
     * Truy vấn danh sách các mục tiêu tiết kiệm của người dùng.
     *
     * @param userId ID người dùng
     * @param status Trạng thái mục tiêu ({@code active}, {@code completed}, {@code cancelled})
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
}
