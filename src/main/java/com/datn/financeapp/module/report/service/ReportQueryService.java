package com.datn.financeapp.module.report.service;

import com.datn.financeapp.module.report.dto.response.CategoryBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.CategoryGroupBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.DailyTrendResponse;
import com.datn.financeapp.module.report.dto.response.MonthlyTrendResponse;
import com.datn.financeapp.module.report.dto.response.ReportHomeResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Interface định nghĩa các dịch vụ truy vấn báo cáo thống kê thu chi, cơ cấu danh mục và xu hướng.
 * Cung cấp các phương thức truy vấn như {@link #home(UUID, String, UUID)}, {@link #summary(UUID, String, LocalDate, LocalDate, UUID)},
 * {@link #byCategoryGroup(UUID, String, LocalDate, LocalDate, String, UUID)}, {@link #byCategory(UUID, String, LocalDate, LocalDate, String, String, int, UUID)},
 * {@link #dailyTrend(UUID, YearMonth, UUID)} và {@link #monthlyTrend(UUID, int)}.
 */
public interface ReportQueryService {

    /**
     * Lấy báo cáo tóm tắt tổng quan thu chi và số dư theo khoảng thời gian.
     *
     * @param userId   ID người dùng
     * @param period   chu kỳ thống kê (month, week, year)
     * @param fromDate ngày bắt đầu (nếu tùy chỉnh)
     * @param toDate   ngày kết thúc (nếu tùy chỉnh)
     * @param walletId ID ví lọc dữ liệu (tùy chọn)
     * @return {@link ReportSummaryResponse} chứa kết quả tóm tắt
     */
    ReportSummaryResponse summary(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId);

    /**
     * Lấy báo cáo phân tích thu chi chia theo nhóm danh mục.
     *
     * @param userId   ID người dùng
     * @param period   chu kỳ thống kê
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param type     loại giao dịch (expense/income)
     * @param walletId ID ví lọc dữ liệu
     * @return {@link CategoryGroupBreakdownResponse} phân tích nhóm danh mục
     */
    CategoryGroupBreakdownResponse byCategoryGroup(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, String type, UUID walletId);

    /**
     * Lấy báo cáo phân tích thu chi chi tiết theo danh mục (cha/con).
     *
     * @param userId   ID người dùng
     * @param period   chu kỳ thống kê
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param type     loại giao dịch
     * @param level    cấp độ danh mục (parent/sub)
     * @param limit    số lượng danh mục giới hạn
     * @param walletId ID ví lọc dữ liệu
     * @return {@link CategoryBreakdownResponse} cơ cấu danh mục
     */
    CategoryBreakdownResponse byCategory(
            UUID userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            String type,
            String level,
            int limit,
            UUID walletId);

    /**
     * Lấy xu hướng thu chi theo từng ngày trong tháng chỉ định.
     *
     * @param userId   ID người dùng
     * @param month    tháng thống kê (YearMonth)
     * @param walletId ID ví lọc dữ liệu
     * @return {@link DailyTrendResponse} xu hướng theo ngày
     */
    DailyTrendResponse dailyTrend(UUID userId, YearMonth month, UUID walletId);

    /**
     * Lấy xu hướng thu chi theo các tháng gần nhất.
     *
     * @param userId      ID người dùng
     * @param monthsCount số tháng truy vấn xu hướng
     * @return {@link MonthlyTrendResponse} xu hướng các tháng
     */
    MonthlyTrendResponse monthlyTrend(UUID userId, int monthsCount);

    /**
     * Lấy thông tin tổng hợp cho trang chủ báo cáo.
     *
     * @param userId   ID người dùng
     * @param period   chu kỳ thống kê
     * @param walletId ID ví lọc dữ liệu
     * @return {@link ReportHomeResponse} thông tin trang chủ báo cáo
     */
    ReportHomeResponse home(UUID userId, String period, UUID walletId);
}
