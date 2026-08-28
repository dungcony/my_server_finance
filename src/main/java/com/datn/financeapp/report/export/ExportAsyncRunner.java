package com.datn.financeapp.report.export;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.report.dto.ExportRequest;
import com.datn.financeapp.report.export.CsvReportWriter.TransactionExportRow;
import com.datn.financeapp.report.repository.ExportJobRepository;
import com.datn.financeapp.report.repository.ReportRepository;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean {@code @Component} RIÊNG khỏi {@code ExportService} — tránh self-invocation mất proxy
 * {@code @Async}, cùng lý do {@code TransactionWriter} (D-31). Chỉ một public method mang
 * {@code @Async}, không gọi method khác cùng bean qua {@code this.xxx()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportAsyncRunner {

    private final ExportJobRepository exportJobRepository;
    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;

    @Value("${app.export.storage-dir:${java.io.tmpdir}/finance-exports}")
    private String storageDir;

    /**
     * Chạy TRONG thread pool riêng ({@code exportTaskExecutor}, xem {@code AsyncConfig}). Dùng
     * CÙNG bộ lọc báo cáo với các điểm cuối đọc ({@code ReportRepository.eligibleTransactions}) —
     * export KHÔNG được xuất toàn bộ giao dịch thô, phải khớp đúng những gì người dùng thấy trên
     * báo cáo.
     */
    @Async("exportTaskExecutor")
    @Transactional
    public void runExport(UUID jobId, UUID userId, ExportRequest req) {
        try {
            LocalDate[] range = resolveRange(req);
            List<Transaction> transactions =
                    reportRepository.eligibleTransactions(userId, range[0], range[1]);

            Map<UUID, Category> categoryCache = new HashMap<>();
            Map<UUID, Wallet> walletCache = new HashMap<>();
            List<TransactionExportRow> rows = transactions.stream()
                    .map(t -> toRow(t, categoryCache, walletCache))
                    .toList();

            Path path = CsvReportWriter.write(Path.of(storageDir), jobId, rows);
            exportJobRepository.markCompleted(jobId, path.toString(), Instant.now().plus(24, ChronoUnit.HOURS));
        } catch (Exception ex) {
            log.error("Xuất báo cáo thất bại cho job {}", jobId, ex);
            exportJobRepository.markFailed(jobId, "Xuất báo cáo thất bại: " + ex.getMessage());
        }
    }

    private TransactionExportRow toRow(
            Transaction t, Map<UUID, Category> categoryCache, Map<UUID, Wallet> walletCache) {
        String categoryName = "";
        if (t.getCategoryId() != null) {
            Category category = categoryCache.computeIfAbsent(
                    t.getCategoryId(), id -> categoryRepository.findById(id).orElse(null));
            categoryName = category == null ? "" : category.getName();
        }
        Wallet wallet =
                walletCache.computeIfAbsent(t.getWalletId(), id -> walletRepository.findById(id).orElse(null));
        String walletName = wallet == null ? "" : wallet.getName();
        return new TransactionExportRow(
                t.getDate().toString(), t.getType(), t.getAmount(), categoryName, walletName, t.getNote());
    }

    private LocalDate[] resolveRange(ExportRequest req) {
        if (req.fromDate() != null && req.toDate() != null) {
            return new LocalDate[] {req.fromDate(), req.toDate()};
        }
        String period = req.period();
        LocalDate today = LocalDate.now();
        if (period == null) {
            YearMonth ym = YearMonth.from(today);
            return new LocalDate[] {ym.atDay(1), ym.atEndOfMonth()};
        }
        return switch (period) {
            case "week" -> {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
                yield new LocalDate[] {start, start.plusDays(6)};
            }
            case "quarter" -> {
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), quarterMonth, 1);
                yield new LocalDate[] {start, start.plusMonths(3).minusDays(1)};
            }
            case "year" -> new LocalDate[] {
                LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31)
            };
            default -> {
                YearMonth ym = YearMonth.from(today);
                yield new LocalDate[] {ym.atDay(1), ym.atEndOfMonth()};
            }
        };
    }
}
