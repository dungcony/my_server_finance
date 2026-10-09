package com.datn.financeapp.module.report.export;

import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.report.dto.request.ExportRequest;
import com.datn.financeapp.module.report.export.CsvReportWriter.TransactionExportRow;
import com.datn.financeapp.module.report.repository.ReportRepository;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final CategoryService categoryService;
    private final WalletService walletService;

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
            LocalDate[] range = resolveRange(req);
            List<Transaction> transactions =
                    // Xuất báo cáo luôn lấy MỌI ví — `wallet_id` không nằm trong tham số của
                    // POST /reports/export (api/06 mục 9), và tệp xuất ra là bản đầy đủ để lưu
                    // trữ chứ không phải một lát cắt đang xem trên màn hình.
                    reportRepository.eligibleTransactions(userId, range[0], range[1], null);

            Map<UUID, CategoryRefResponse> categoryCache = new HashMap<>();
            Map<UUID, WalletRefResponse> walletCache = new HashMap<>();
            List<TransactionExportRow> rows = transactions.stream()
                    .map(t -> toRow(t, categoryCache, walletCache))
                    .toList();

            Path path = CsvReportWriter.write(Path.of(storageDir), jobId, rows);
            exportStatusWriter.markCompleted(
                    jobId, path.toString(), Instant.now().plus(24, ChronoUnit.HOURS));
        } catch (Exception ex) {
            log.error("Xuất báo cáo thất bại cho job {}", jobId, ex);
            try {
                exportStatusWriter.markFailed(jobId, "Xuất báo cáo thất bại: " + ex.getMessage());
            } catch (Exception markEx) {
                // Ghi trạng thái lỗi cũng hỏng (CSDL sập hẳn chẳng hạn) — bản ghi sẽ nằm lại ở
                // processing. Job dọn ExportCleanupJob quét các bản ghi processing quá cũ để tự
                // phục hồi, nên ở đây chỉ cần log, không nuốt im lặng và cũng không ném tiếp ra
                // thread pool @Async (không ai bắt được, chỉ làm bẩn log với stack trace kép).
                log.error("Không ghi được trạng thái thất bại cho job {}", jobId, markEx);
            }
        }
    }

    private TransactionExportRow toRow(
            Transaction t,
            Map<UUID, CategoryRefResponse> categoryCache,
            Map<UUID, WalletRefResponse> walletCache) {
        String categoryName = "";
        if (t.getCategoryId() != null) {
            CategoryRefResponse category =
                    categoryCache.computeIfAbsent(t.getCategoryId(), categoryService::findRefById);
            categoryName = category == null ? "" : category.name();
        }
        WalletRefResponse wallet =
                walletCache.computeIfAbsent(t.getWalletId(), walletService::findRefById);
        String walletName = wallet == null ? "" : wallet.name();
        return new TransactionExportRow(
                t.getDate().toString(), t.getType(), t.getAmount(), categoryName, walletName, t.getNote());
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
