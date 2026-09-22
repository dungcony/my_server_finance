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
 *     <li>Báo cáo cân đối thành viên (Balance/Settlement Report): Chi tiết số tiền từng thành viên đã đóng, chi hộ, rút ra, phần chi tiêu phải gánh, và số dư ròng thừa/thiếu.</li>
 * </ul>
 * </p>
 */
public interface GroupReportService {

    /**
     * Lấy báo cáo tổng quan tình hình tài chính của nhóm theo một tháng cụ thể (hoặc toàn thời gian nếu không truyền tháng).
     *
     * @param userId  ID người dùng yêu cầu (phải là thành viên nhóm)
     * @param groupId ID nhóm
     * @param month   Tháng cần báo cáo (định dạng {@code YYYY-MM}), truyền {@code null} để lấy toàn thời gian
     * @return Báo cáo tổng quan gồm số dư quỹ hiện tại, tổng thu, tổng chi, biểu đồ theo danh mục
     */
    GroupSummaryReportRes getSummary(UUID userId, UUID groupId, String month);

    /**
     * Phương thức tiện ích lấy báo cáo tổng quan toàn thời gian của nhóm.
     *
     * @param userId  ID người dùng yêu cầu
     * @param groupId ID nhóm
     * @return Báo cáo tổng quan toàn thời gian
     */
    default GroupSummaryReportRes getSummary(UUID userId, UUID groupId) {
        return getSummary(userId, groupId, null);
    }

    /**
     * Lấy báo cáo cân đối thu chi (bảng tính nợ/thừa thiếu) của từng thành viên trong nhóm.
     * <p>
     * Dựa trên thuật toán cân đối tài chính:
     * <ul>
     *     <li>Tiền đã đóng góp còn lại (Contributed)</li>
     *     <li>Tiền tự bỏ túi chi hộ nhóm (Paid out of pocket)</li>
     *     <li>Tiền đã hoàn trả và rút ra (Refunded, Withdrawn)</li>
     *     <li>Phần chi tiêu phải chịu (Share)</li>
     *     <li>Số dư ròng (Net Balance): Dương là nhóm nợ thành viên, Âm là thành viên nợ nhóm</li>
     *     <li>Số tiền cần đóng thêm (Needed) nếu nhóm có bật tính năng hạn mức (Target/Settlement)</li>
     * </ul>
     * </p>
     *
     * @param userId  ID người dùng yêu cầu
     * @param groupId ID nhóm
     * @return Bảng chi tiết cân đối của từng thành viên
     */
    GroupBalanceReportRes getBalances(UUID userId, UUID groupId);
}
