package com.datn.financeapp.module.notification.enums;

/**
 * Loại thông báo, lưu ở cột {@code notifications.type} bằng tên hằng.
 * App dựa vào loại để chọn icon và màn hình đích.
 */
public enum NotiType {
    BUDGET_ALERT,              // ngân sách sắp hết hoặc đã vượt; reference_id là id ngân sách
    DEBT_REMINDER,             // khoản nợ đến hạn hoặc quá hạn; reference_id là id khoản nợ
    RECURRING_GENERATED,       // giao dịch định kỳ vừa được sinh
    BUDGET_RENEWED,            // ngân sách tự gia hạn sang kỳ mới; reference_id là id ngân sách kỳ mới
    GOAL_COMPLETED,            // mục tiêu tiết kiệm hoàn thành
    GROUP_TXN_REVIEW_REQUEST,  // gửi chủ nhóm/thủ quỹ: giao dịch nhóm của thành viên cần duyệt
    GROUP_TXN_RECORD_REQUEST,  // gửi người liên quan: giao dịch nhóm đã xác nhận, hỏi có muốn ghi vào sổ cá nhân không
    PAYMENT_TOPUP_REQUIRED,    // đơn thanh toán thiếu tiền, yêu cầu nạp bù nốt phần còn thiếu
    PAYMENT_TOPUP_REMINDER,    // nhắc nhở sắp hết hạn bù tiền cho đơn thanh toán thiếu
    PAYMENT_FORFEITED          // đơn thanh toán quá hạn bù tiền bị đóng và giữ tiền
}
