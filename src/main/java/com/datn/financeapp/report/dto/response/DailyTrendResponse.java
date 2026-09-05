package com.datn.financeapp.report.dto.response;

import java.util.List;

/**
 * {@code GET /reports/daily-trend} (api/06-BAO-CAO.md mục 5). {@code currentLine} CHỈ chạy tới
 * ngày hôm nay khi tháng đang xét là tháng hiện tại — vẽ phẳng tới cuối tháng sẽ khiến người dùng
 * tưởng họ ngừng tiêu (api/06 mục 5 "Điểm quan trọng").
 */
public record DailyTrendResponse(
        String month,
        int daysInMonth,
        List<CurrentPoint> currentLine,
        List<AveragePoint> avg3MonthsLine,
        Long currentTotal,
        Long avg3MonthsSamePoint,
        Assessment assessment) {

    public record CurrentPoint(int date, Long spentThatDay, Long cumulative) {}

    public record AveragePoint(int date, Long cumulative) {}

    public record Assessment(String label, Double ratioVsAverage, String content) {}
}
