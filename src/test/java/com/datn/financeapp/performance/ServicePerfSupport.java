package com.datn.financeapp.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Khung dùng chung của các test đo hiệu năng từng hàm service ({@code UserServicePerfIntegrationTest},
 * {@code AuthServicePerfIntegrationTest}, {@code GroupServicePerfIntegrationTest}).
 *
 * <p>Mỗi hàm được đo ở hai hồ sơ dữ liệu {@link Profile#TOT_NHAT} và {@link Profile#TE_NHAT}. Số câu SQL
 * <b>tăng</b> từ hồ sơ tốt nhất sang tệ nhất là dấu hiệu N+1; số tuyệt đối ở hồ sơ tệ nhất là chặn trên
 * của hàm đó.
 *
 * <p>Một lần đo gồm: chạy {@code setup} (dựng dữ liệu riêng cho lần gọi), gọi hàm một lần để khởi động nguội,
 * chạy {@code setup} lần nữa, rồi gọi hàm lần hai và đo. Vì mỗi lần gọi có dữ liệu mới nên hàm ghi hay hàm
 * phá huỷ (xoá, rời nhóm...) đo được giống hàm đọc. {@code setup} không được tính vào số đo.
 *
 * <p>Lỗi ở bất kỳ bước nào chỉ được ghi vào cột ghi chú của hàm đó, không làm đỏ test, để một hàm có dữ liệu
 * mẫu sai hay vướng quy tắc nghiệp vụ không che mất số liệu của các hàm còn lại.
 *
 * <p>Số câu SQL lấy từ {@link SqlCountingConfig}, nên test dùng khung này phải {@code @Import} nó.
 */
@Slf4j
public class ServicePerfSupport {

    public enum Profile {
        TOT_NHAT("TỐT NHẤT"),
        TE_NHAT("TỆ NHẤT");

        private final String label;

        Profile(String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    public interface Step {
        void run() throws Exception;
    }

    private record Result(long sql, long ms, String error) {
        boolean failed() {
            return error != null;
        }
    }

    private static final int MAX_ERROR_LENGTH = 90;
    private static final String SKIPPED_PREFIX = "BỎ QUA: ";

    private final String module;
    private final Map<String, Map<Profile, Result>> results = new LinkedHashMap<>();

    public ServicePerfSupport(String module) {
        this.module = module;
    }

    // hàm không cần dựng dữ liệu riêng cho từng lần gọi
    public void measure(Profile profile, String label, Step action) {
        measure(profile, label, () -> { }, action);
    }

    public void measure(Profile profile, String label, Step setup, Step action) {
        Result result;
        try {
            setup.run();
            action.run();
            setup.run();
            result = timed(action);
        } catch (Throwable t) {
            result = new Result(-1, -1, describe(t));
        }
        results.computeIfAbsent(label, key -> new EnumMap<>(Profile.class)).put(profile, result);
    }

    // hàm không đo được (vd gọi dịch vụ bên ngoài): vẫn xuất hiện trong báo cáo kèm lý do để không bị bỏ sót
    public void skip(String label, String reason) {
        Result skipped = new Result(-1, -1, SKIPPED_PREFIX + reason);
        Map<Profile, Result> byProfile = results.computeIfAbsent(label, key -> new EnumMap<>(Profile.class));
        byProfile.put(Profile.TOT_NHAT, skipped);
        byProfile.put(Profile.TE_NHAT, skipped);
    }

    // ghi báo cáo ra log (WARN) và file target/perf-<module>.txt rồi trả lại nội dung báo cáo
    public String writeReport(String bestDescription, String worstDescription) throws IOException {
        String report = buildReport(bestDescription, worstDescription);
        log.warn("\n{}", report);
        Path file = Path.of("target", "perf-" + module + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report);
        return report;
    }

    public int measuredFunctionCount() {
        return results.size();
    }

    private Result timed(Step action) {
        long sqlBefore = SqlCountingConfig.sqlCount();
        long start = System.nanoTime();
        String error = null;
        try {
            action.run();
        } catch (Throwable t) {
            error = describe(t);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new Result(SqlCountingConfig.sqlCount() - sqlBefore, ms, error);
    }

    String buildReport(String bestDescription, String worstDescription) {
        StringBuilder out = new StringBuilder();
        out.append("[PERF] ").append(module).append(" — ")
                .append(Profile.TOT_NHAT.label).append(" (").append(bestDescription).append(") -> ")
                .append(Profile.TE_NHAT.label).append(" (").append(worstDescription).append(")\n");
        out.append(String.format("%-58s %8s %7s %6s %8s %7s  %s%n",
                "hàm", "sql_tot", "sql_te", "tang", "ms_tot", "ms_te", "ghi_chu"));

        for (String label : sortedLabels()) {
            Result best = results.get(label).get(Profile.TOT_NHAT);
            Result worst = results.get(label).get(Profile.TE_NHAT);
            out.append(String.format("%-58s %8s %7s %6s %8s %7s  %s%n",
                    label, sql(best), sql(worst), growth(best, worst), ms(best), ms(worst), note(best, worst)));
        }
        return out.toString();
    }

    // hàm tốn SQL nhất ở hồ sơ tệ nhất lên đầu, hàm bị lỗi xuống cuối
    private List<String> sortedLabels() {
        List<String> labels = new ArrayList<>(results.keySet());
        labels.sort(Comparator
                .comparing((String label) -> failedAnywhere(label))
                .thenComparing(Comparator.comparingLong((String label) -> worstSql(label)).reversed()));
        return labels;
    }

    private boolean failedAnywhere(String label) {
        return results.get(label).values().stream().anyMatch(Result::failed);
    }

    private long worstSql(String label) {
        Result worst = results.get(label).get(Profile.TE_NHAT);
        return worst == null ? -1 : worst.sql();
    }

    private String note(Result best, Result worst) {
        if (best != null && best.failed())
            return best.error().startsWith(SKIPPED_PREFIX) ? best.error() : "LỖI (tốt nhất): " + best.error();
        if (worst != null && worst.failed())
            return "LỖI (tệ nhất): " + worst.error();
        if (best != null && worst != null && worst.sql() > best.sql())
            return "NGHI N+1 (SQL tăng theo dữ liệu)";
        return "";
    }

    private String growth(Result best, Result worst) {
        if (best == null || worst == null || best.failed() || worst.failed())
            return "-";
        return String.format("%+d", worst.sql() - best.sql());
    }

    private String sql(Result result) {
        return result == null || result.failed() ? "-" : String.valueOf(result.sql());
    }

    private String ms(Result result) {
        return result == null || result.failed() ? "-" : String.valueOf(result.ms());
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        String message = root.getMessage() == null ? "" : ": " + root.getMessage().replaceAll("\\s+", " ");
        String text = root.getClass().getSimpleName() + message;
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) + "..." : text;
    }
}
