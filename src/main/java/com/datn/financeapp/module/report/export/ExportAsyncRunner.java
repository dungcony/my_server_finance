package com.datn.financeapp.module.report.export;

import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.report.dto.request.ExportRequest;
import com.datn.financeapp.module.report.export.CsvReportWriter.TransactionExportRow;
import com.datn.financeapp.module.report.repository.ReportRepository;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Component hạ tầng thực thi bất đồng bộ tiến trình xuất báo cáo CSV trên thread pool riêng.
 * Cung cấp phương thức chính {@link #runExport(UUID, UUID, ExportRequest)} được đánh dấu @Async.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportAsyncRunner {

    private final ExportStatusWriter exportStatusWriter;
    private final ReportRepository reportRepository;
    private final BudgetCategoryHelper budgetCategoryHelper;
    private final WalletService walletService;
    private final UserTimezoneHelper timezoneHelper;

    @Value("${app.export.storage-dir:${java.io.tmpdir}/finance-exports}")
    private String storageDir;

    /**
     * Thực thi tiến trình xuất dữ liệu báo cáo bất đồng bộ.
     *
     * @param jobId  ID tiến trình xuất dữ liệu
     * @param userId ID người dùng yêu cầu
     * @param req    thông tin tham số xuất dữ liệu
     */
    @Async("exportTaskExecutor")
    public void runExport(UUID jobId, UUID userId, ExportRequest req) {
        try {
            ZoneId zone = timezoneHelper.getCurrentZone();
            LocalDate[] range = resolveRange(req);
            Instant startInstant = timezoneHelper.toStartInstant(range[0], zone);
            Instant endInstant = timezoneHelper.toEndInstant(range[1], zone);

            List<Transaction> transactions =
                    reportRepository.eligibleTransactions(userId, startInstant, endInstant, null);

            Map<String, CateSumaryRes> categoryCache = new HashMap<>();
            Map<UUID, WalletSumaryRes> walletCache = new HashMap<>();
            List<TransactionExportRow> rows = transactions.stream()
                    .map(t -> toRow(t, userId, zone, categoryCache, walletCache))
                    .toList();

            Path path = CsvReportWriter.write(Path.of(storageDir), jobId, rows);
            exportStatusWriter.markCompleted(
                    jobId, path.toString(), Instant.now().plus(24, ChronoUnit.HOURS));
        } catch (Exception ex) {
            log.error("Xuất báo cáo thất bại cho job {}", jobId, ex);
            try {
                exportStatusWriter.markFailed(jobId, "Xuất báo cáo thất bại: " + ex.getMessage());
            } catch (Exception markEx) {
                log.error("Không ghi được trạng thái thất bại cho job {}", jobId, markEx);
            }
        }
    }

    private TransactionExportRow toRow(
            Transaction t,
            UUID userId,
            ZoneId zone,
            Map<String, CateSumaryRes> categoryCache,
            Map<UUID, WalletSumaryRes> walletCache) {
        String categoryName = "";
        if (t.getCategoryCode() != null && !t.getCategoryCode().isBlank()) {
            CateSumaryRes category = categoryCache.computeIfAbsent(
                    t.getCategoryCode(),
                    code -> budgetCategoryHelper.findSummary(code).orElse(null));
            categoryName = category == null ? t.getCategoryCode() : category.name();
        }

        WalletSumaryRes wallet = walletCache.computeIfAbsent(
                t.getWalletId(),
                id -> walletService.findSumaryByIdAndUser(id, userId, true).orElse(null));
        String walletName = wallet == null ? "" : wallet.name();

        String dateStr = t.getOccurredAt() != null
                ? timezoneHelper.toLocalDate(t.getOccurredAt(), zone).toString()
                : "";

        String note = t.getNote() != null && !t.getNote().isBlank()
                ? t.getNote()
                : (t.getName() != null ? t.getName() : "");

        return new TransactionExportRow(
                dateStr,
                t.getType() != null ? t.getType().name() : "",
                t.getAmount() != null ? t.getAmount() : 0L,
                categoryName,
                walletName,
                note);
    }

    private LocalDate[] resolveRange(ExportRequest req) {
        if (req.fromDate() != null && req.toDate() != null) {
            return new LocalDate[]{req.fromDate(), req.toDate()};
        }
        String period = req.period();
        LocalDate today = LocalDate.now();
        if (period == null) {
            YearMonth ym = YearMonth.from(today);
            return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
        }
        return switch (period) {
            case "week" -> {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
                yield new LocalDate[]{start, start.plusDays(6)};
            }
            case "quarter" -> {
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), quarterMonth, 1);
                yield new LocalDate[]{start, start.plusMonths(3).minusDays(1)};
            }
            case "year" -> new LocalDate[]{
                    LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31)
            };
            default -> {
                YearMonth ym = YearMonth.from(today);
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
        };
    }
}
