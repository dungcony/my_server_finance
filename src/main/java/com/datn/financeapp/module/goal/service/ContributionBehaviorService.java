package com.datn.financeapp.module.goal.service;

import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalUpdateReq;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dịch vụ xử lý các nghiệp vụ nạp tiền, rút tiền và điều chỉnh đợt đóng góp của mục tiêu tiết kiệm.
 */
public interface ContributionBehaviorService {

    /**
     * Ghi nhận khoản nộp tiền vào mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu tiết kiệm
     * @param req     thông tin nộp tiền
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin khoản đóng góp vừa tạo
     */
    ContributionGoalRes addContribution(UUID goalId, ContributionGoalCreateReq req, boolean isAdmin);

    /**
     * Ghi nhận khoản rút tiền từ mục tiêu tiết kiệm.
     *
     * @param goalId  ID mục tiêu tiết kiệm
     * @param req     thông tin rút tiền
     * @param isAdmin cờ đánh dấu người gọi là admin
     * @return DTO thông tin khoản rút tiền vừa tạo
     */
    default ContributionGoalRes withdraw(UUID goalId, ContributionGoalCreateReq req, boolean isAdmin) {
        return addContribution(goalId, req.withdraw(), isAdmin);
    }

    /**
     * lấy chi tiết đóng góp
     *
     * @param goalId id mục tiêu gốc
     */
    ContributionGoalListRes gets(UUID goalId, ContributionGoalFilterReq filter, Pageable pageable, boolean isAdmin);

    /**
     * Cập nhật thông tin một đợt đóng góp đã ghi nhận.
     *
     * @param goalId         ID mục tiêu tiết kiệm
     * @param contributionId ID đợt đóng góp
     * @param req            thông tin cập nhật
     * @param isAdmin        cờ đánh dấu người gọi là admin
     * @return DTO thông tin đợt đóng góp sau cập nhật
     */
    ContributionGoalRes updateContribution(UUID goalId, UUID contributionId, ContributionGoalUpdateReq req, boolean isAdmin);

    void deleteContribution(UUID goalId, UUID contributionId, boolean isAdmin);
}
