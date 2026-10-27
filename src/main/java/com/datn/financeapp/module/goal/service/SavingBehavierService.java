package com.datn.financeapp.module.goal.service;

import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalUpdateRequest;
import com.datn.financeapp.module.goal.dto.request.SavingGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalUpdateRequest;
import com.datn.financeapp.module.goal.dto.request.WithdrawGoalRequest;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dịch vụ xử lý các nghiệp vụ liên quan đến mục tiêu tiết kiệm cho cả User và Admin.
 */
public interface GoalBehavierService {

    /**
     * Tạo mới mục tiêu tiết kiệm cho người dùng hiện tại.
     *
     * @param req     thông tin tạo mục tiêu
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin mục tiêu vừa tạo
     */
    SavingGoalRes create(SavingGoalCreateReq req, boolean isAdmin);

    /**
     * Tạo mới mục tiêu tiết kiệm cho một người dùng cụ thể (Admin tạo hộ).
     *
     * @param targetUserId ID người dùng đích
     * @param req          thông tin tạo mục tiêu
     * @param isAdmin      cờ đánh dấu người gọi là admin
     * @return DTO thông tin mục tiêu vừa tạo
     */
    SavingGoalRes create(UUID targetUserId, SavingGoalCreateReq req, boolean isAdmin);

    /**
     * Cập nhật thông tin mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu
     * @param req     thông tin cập nhật
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin mục tiêu sau cập nhật
     */
    SavingGoalRes update(UUID goalId, SavingGoalUpdateRequest req, boolean isAdmin);

    /**
     * Xóa mềm mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu cần xóa
     * @param isAdmin cờ đánh dấu người gọi là admin
     */
    void delete(UUID goalId, boolean isAdmin);

    /**
     * Lấy chi tiết thông tin mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO chi tiết mục tiêu
     */
    SavingGoalRes detail(UUID goalId, boolean isAdmin);

    /**
     * Nộp tiền vào mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu
     * @param req     thông tin nộp tiền
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin khoản đóng góp
     */
    ContributionGoalRes addContribution(UUID goalId, ContributionGoalCreateReq req, boolean isAdmin);

    /**
     * Rút tiền khỏi mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu
     * @param req     thông tin rút tiền
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin khoản rút tiền
     */
    ContributionGoalRes withdraw(UUID goalId, WithdrawGoalRequest req, boolean isAdmin);

    /**
     * Cập nhật thông tin một đợt đóng góp.
     *
     * @param goalId         ID mục tiêu
     * @param contributionId ID đợt đóng góp
     * @param req            thông tin cập nhật
     * @param isAdmin        cờ đánh dấu người gọi là admin
     * @return DTO thông tin đợt đóng góp sau cập nhật
     */
    ContributionGoalRes updateContribution(UUID goalId, UUID contributionId, ContributionGoalUpdateRequest req, boolean isAdmin);

    /**
     * Hủy bỏ một đợt đóng góp tiết kiệm.
     *
     * @param goalId         ID mục tiêu
     * @param contributionId ID đợt đóng góp cần hủy
     * @param isAdmin        cờ đánh dấu người gọi là admin
     */
    void cancelContribution(UUID goalId, UUID contributionId, boolean isAdmin);

    /**
     * Lấy danh sách mục tiêu tiết kiệm theo bộ lọc và phân trang.
     *
     * @param filter   bộ lọc tìm kiếm
     * @param pageable thông tin phân trang
     * @param isAdmin  cờ đánh dấu người gọi là admin
     * @return danh sách mục tiêu phân trang
     */
    SavingGoalListRes gets(SavingGoalFilterReq filter, Pageable pageable, boolean isAdmin);
}
