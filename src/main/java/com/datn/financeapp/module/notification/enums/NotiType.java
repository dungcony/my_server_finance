package com.datn.financeapp.module.notification.enums;

/**
 * Loại thông báo, lưu ở cột {@code notifications.type} bằng tên hằng. App dựa vào loại để chọn icon
 * và màn hình đích; {@code reference_id} của thông báo trỏ tới đối tượng tuỳ theo loại.
 */
public enum NotiType {
    BUDGET_ALERT,              // ngân sách sắp hết hoặc đã vượt; reference_id là id ngân sách
    DEBT_REMINDER,             // khoản nợ đến hạn hoặc quá hạn; reference_id là id khoản nợ
    RECURRING_GENERATED,       // giao dịch định kỳ vừa được sinh; chưa có nơi nào sinh loại này
    BUDGET_RENEWED,            // ngân sách tự gia hạn sang kỳ mới; reference_id là id ngân sách kỳ mới
    GOAL_COMPLETED,            // mục tiêu tiết kiệm hoàn thành; chưa có nơi nào sinh loại này
    GROUP_TXN_REVIEW_REQUEST,  // gửi chủ nhóm/thủ quỹ: giao dịch nhóm của thành viên cần duyệt; reference_id là id giao dịch nhóm
    GROUP_TXN_RECORD_REQUEST   // gửi người liên quan: giao dịch nhóm đã xác nhận, hỏi có muốn ghi vào sổ cá nhân không; data mang thông tin điền sẵn, reference_id là id giao dịch nhóm
}
