package com.datn.financeapp.module.goal.service;

import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.dto.request.SavingGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalUpdateRequest;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dịch vụ xử lý các nghiệp vụ quản lý vòng đời mục tiêu tiết kiệm cho cả User và Admin.
 */
public interface SavingBehavierService {

    /**
     * admin tạo
     *
     * @param req thông tin tạo mục tiêu
     * @return DTO thông tin mục tiêu vừa tạo
     */
    SavingGoalRes create(UUID userId, SavingGoalCreateReq req);

    /**
     * user tự tạo
     */
    default SavingGoalRes create(SavingGoalCreateReq req) {
        return create(SecurityContextUtil.currentUserId(), req);
    }

    /**
     * Lấy chi tiết thông tin mục tiêu tiết kiệm.
     *
     * @param goalId ID mục tiêu
     * @return DTO chi tiết mục tiêu
     */
    SavingGoalRes detail(UUID goalId, boolean isAdmin);

    /**
     * Lấy danh sách mục tiêu tiết kiệm theo bộ lọc và phân trang.
     *
     * @param filter   bộ lọc tìm kiếm
     * @param pageable thông tin phân trang
     * @return danh sách mục tiêu phân trang
     */
    SavingGoalListRes gets(UUID userId, SavingGoalFilterReq filter, Pageable pageable);

    /**
     * user tự lọc
     */
    default SavingGoalListRes gets(SavingGoalFilterReq filter, Pageable pageable) {
        return gets(SecurityContextUtil.currentUserId(), filter, pageable);
    }

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
    
}
