package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;

import java.util.UUID;

/**
 * Service báo cáo và phân tích tài chính nhóm (Group Reporting).
 * <p>
 * Phụ trách việc tổng hợp số liệu, xuất báo cáo tổng quan và bảng đối chiếu cân đối thu chi:
 * <ul>
 *     <li>Báo cáo tổng quan (Summary Report): Biến động dòng tiền theo tháng (hoặc toàn thời gian), số dư quỹ, tổng chi, tổng đóng góp.</li>
 *     <li>Báo cáo cân đối thành viên (Balance/Settlement Report): Chi tiết số tiền từng thành viên đã chi hộ, đã được hoàn, phần chi tiêu phải gánh, và số dư ròng thừa/thiếu.</li>
 * </ul>
 * </p>
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #getSummary}: Báo cáo tổng quan (cả 2 overload). Input: operatorId, groupId, month(optional). Output: GroupSummaryReportRes.</li>
 *   <li>{@link #getBalances}: Báo cáo cân đối thu chi. Input: operatorId, groupId. Output: GroupBalanceReportRes.</li>
 * </ul>
 * </p>
 */
public interface ReportService {

    /**
     * Lấy báo cáo tổng quan tình hình tài chính của nhóm theo một tháng cụ thể (hoặc toàn thời gian nếu không truyền tháng).
     *
     * @param operatorId ID người dùng yêu cầu (phải là thành viên nhóm)
     * @param groupId    ID nhóm
     * @param month      Tháng cần báo cáo (định dạng {@code YYYY-MM}), truyền {@code null} để lấy toàn thời gian
     * @return Báo cáo tổng quan gồm số dư quỹ hiện tại, tổng thu, tổng chi, biểu đồ theo danh mục
     */
    GroupSummaryReportRes getSummary(UUID operatorId, UUID groupId, String month);
    
    /**
     * Lấy báo cáo cân đối thu chi (bảng tính nợ/thừa thiếu) của từng thành viên trong nhóm.
     * <p>
     * Dựa trên thuật toán cân đối tài chính:
     * <ul>
     *     <li>Tiền đã đóng góp còn lại (Contributed)</li>
     *     <li>Tiền tự bỏ túi chi hộ nhóm (Paid out of pocket)</li>
     *     <li>Tiền đã được quỹ trả lại (Refunded)</li>
     *     <li>Phần chi tiêu phải chịu (Share)</li>
     *     <li>Số dư ròng (Net Balance): Dương là nhóm nợ thành viên, Âm là thành viên nợ nhóm</li>
     *     <li>Số tiền cần đóng thêm (Needed) nếu nhóm có bật tính năng hạn mức (Target/Settlement)</li>
     * </ul>
     * </p>
     *
     * @param operatorId ID người dùng yêu cầu
     * @param groupId    ID nhóm
     * @return Bảng chi tiết cân đối của từng thành viên
     */
    GroupBalanceReportRes getBalances(UUID operatorId, UUID groupId);
}
