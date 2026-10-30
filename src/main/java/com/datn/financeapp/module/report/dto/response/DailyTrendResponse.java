package com.datn.financeapp.module.report.dto.response;

import java.util.List;

/**
 * Response DTO cho báo cáo xu hướng chi tiêu từng ngày trong tháng.
 *
 * @param month               tháng thống kê (dạng YYYY-MM)
 * @param daysInMonth         số ngày trong tháng
 * @param currentLine         danh sách các điểm dữ liệu chi tiêu thực tế từng ngày
 * @param avg3MonthsLine      danh sách các điểm dữ liệu trung bình 3 tháng trước
 * @param currentTotal        tổng chi tiêu thực tế tính đến thời điểm xem
 * @param avg3MonthsSamePoint số tiền trung bình 3 tháng trước tại cùng mốc ngày
 * @param assessment          thông tin đánh giá xu hướng chi tiêu
 */
public record DailyTrendResponse(
        String month,
        int daysInMonth,
        List<CurrentPoint> currentLine,
        List<AveragePoint> avg3MonthsLine,
        Long currentTotal,
        Long avg3MonthsSamePoint,
        Assessment assessment) {

    /**
     * Điểm dữ liệu chi tiêu thực tế theo ngày.
     *
     * @param date         ngày trong tháng
     * @param spentThatDay số tiền chi trong ngày đó
     * @param cumulative   tổng chi tiêu dồn đến ngày đó
     */
    public record CurrentPoint(int date, Long spentThatDay, Long cumulative) {
    }

    /**
     * Điểm dữ liệu chi tiêu trung bình theo ngày.
     *
     * @param date       ngày trong tháng
     * @param cumulative tổng chi tiêu trung bình dồn đến ngày đó
     */
    public record AveragePoint(int date, Long cumulative) {
    }

    /**
     * Đánh giá mức độ chi tiêu so với trung bình.
     *
     * @param label          nhãn đánh giá (Bình thường, Cao hơn, Thấp hơn)
     * @param ratioVsAverage tỷ lệ chênh lệch so với trung bình
     * @param content        nội dung giải thích chi tiết
     */
    public record Assessment(String label, Double ratioVsAverage, String content) {
    }
}
