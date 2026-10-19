package com.datn.financeapp.module.debt.service;

import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.module.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.module.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.module.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.module.debt.dto.response.DebtSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý các nghiệp vụ sổ nợ dành riêng cho Quản trị viên (Admin).
 * Độc lập với Service của User để đảm bảo tính module hóa và dễ bảo trì, debug.
 */
public interface DebtAdminService {

    /**
     * Lấy danh sách khoản nợ (có thể lọc theo userId, loại nợ, trạng thái, quá hạn).
     */
    List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue);

    /**
     * Lấy chi tiết bất kỳ khoản nợ nào trong hệ thống kèm lịch sử trả và giao dịch gốc.
     */
    DebtDetailResponse detail(UUID debtId);

    /**
     * Lấy báo cáo tổng quan công nợ toàn hệ thống hoặc theo một user cụ thể.
     */
    DebtSummaryResponse summary(UUID userId);

    /**
     * Tạo mới khoản nợ hộ cho một user chỉ định.
     */
    CreateDebtResponse createForUser(UUID targetUserId, CreateDebtRequest req);

    /**
     * Ghi nhận thanh toán trả nợ/thu nợ hộ user.
     */
    CreatePaymentResponse addPaymentForUser(UUID debtId, CreatePaymentRequest req);

    /**
     * Cập nhật thông tin khoản nợ (đối tác, hạn trả, ghi chú).
     */
    DebtListItemResponse update(UUID debtId, UpdateDebtRequest req);

    /**
     * Xóa nợ xấu (write-off) cho khoản nợ.
     */
    DebtListItemResponse writeOff(UUID debtId, WriteOffRequest req);

    /**
     * Hủy bỏ một đợt thanh toán nợ và hoàn tác ví.
     */
    void cancelPayment(UUID debtId, UUID paymentId);

    /**
     * Cưỡng chế xóa khoản nợ và hoàn tác toàn bộ giao dịch liên quan.
     */
    void delete(UUID debtId);
}
