package com.datn.financeapp.module.group.dto.response.report;

import java.util.UUID;

/**
 * Thông tin số dư và trạng thái tài chính của một thành viên trong nhóm.
 *
 * @param userId             ID của thành viên
 * @param fullName           Họ và tên hiển thị của thành viên
 * @param status             Trạng thái hoạt động của thành viên trong nhóm (ví dụ: ACTIVE, LEFT, REMOVED)
 * @param totalRefunded      Tổng số tiền quỹ nhóm đã hoàn trả lại cho thành viên
 * @param totalShareAmount   Tổng số tiền thành viên phải cùng gánh chịu từ các khoản chi tiêu chung
 * @param netBalance         Số dư ròng trong quỹ (dương: quỹ nhóm giữ tiền của thành viên; âm: thành viên đang nợ nhóm)
 * @param neededContribution Số tiền cần nộp thêm vào quỹ để đưa số dư về 0 (bằng giá trị tuyệt đối nếu netBalance âm, bằng 0 nếu netBalance dương)
 */
public record GroupBalanceItemRes(
        UUID userId,
        String fullName,
        String status,
        Long totalRefunded,
        Long totalShareAmount,
        Long netBalance,
        Long neededContribution) {
}
