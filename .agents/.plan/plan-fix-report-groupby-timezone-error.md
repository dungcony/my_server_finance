# Sửa lỗi GROUP BY chứa tham số múi giờ trong ReportRepository

## Bối cảnh

Khi người dùng đăng ký tài khoản, xác thực email OTP thành công, ứng dụng điều hướng về trang chủ và gọi API `GET /v1/reports/home`.
Hệ thống báo lỗi PostgreSQL:
`PSQLException: ERROR: column "t.occurred_at" must appear in the GROUP BY clause or be used in an aggregate function`.
Nguyên nhân do trong `ReportRepository.java`, các phương thức `dailyExpense` và `monthlyTrend` sử dụng tham số `:timeZone` lặp lại ở cả mệnh đề `SELECT` và `GROUP BY`. Khi Spring Data JPA chuyển đổi named parameter thành positional parameters JDBC (`?`), PostgreSQL nhận 2 tham số riêng biệt nên không xem hai biểu thức là giống nhau, dẫn đến từ chối câu truy vấn.

## Quyết định đã chốt

- Sử dụng `GROUP BY 1` trong câu lệnh SQL native thay vì lặp lại biểu thức chứa tham số `:timeZone` ở cả `dailyExpense` và `monthlyTrend`.
- `GROUP BY 1` là cú pháp chuẩn của PostgreSQL, gom nhóm trực tiếp theo cột thứ 1 trong danh sách `SELECT` (`date` hoặc `month`), loại bỏ hoàn toàn việc truyền trùng tham số JDBC và xử lý triệt để lỗi.

## 1. Các file sẽ thay đổi

**Code**
- `src/main/java/com/datn/financeapp/module/report/repository/ReportRepository.java:296,329`:
  - Dòng 296 (`dailyExpense`): đổi `GROUP BY (t.occurred_at AT TIME ZONE :timeZone)::date` thành `GROUP BY 1`.
  - Dòng 329 (`monthlyTrend`): đổi `GROUP BY date_trunc('month', t.occurred_at AT TIME ZONE :timeZone)::date` thành `GROUP BY 1`.

## 2. Lý do thay đổi

- Sửa lỗi cú pháp PostgreSQL làm hỏng API `GET /v1/reports/home` khi tải dữ liệu trang chủ sau khi xác thực OTP.
- Vị trí phát sinh: `ReportRepository.java:296` (được gọi từ `ReportQueryServiceImpl.java:263`).

## 3. Hướng giải quyết / code xem trước

### Cập nhật `ReportRepository.java`

Tại method `dailyExpense`:
```java
                    SELECT
                      (t.occurred_at AT TIME ZONE :timeZone)::date AS date,
                      CAST(SUM(t.amount) AS BIGINT) AS amount
                    FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = 'EXPENSE'
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY 1
                    ORDER BY date
```

Tại method `monthlyTrend`:
```java
                    SELECT
                      date_trunc('month', t.occurred_at AT TIME ZONE :timeZone)::date AS month,
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount END), 0) AS BIGINT) AS total_income,
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount END), 0) AS BIGINT) AS total_expense
                    FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY 1
                    ORDER BY month
```

## 4. Danh sách công việc chi tiết

- Sửa mệnh đề `GROUP BY` tại method `dailyExpense` trong `ReportRepository.java`.
- Sửa mệnh đề `GROUP BY` tại method `monthlyTrend` trong `ReportRepository.java`.
- Kiểm tra biên dịch mã nguồn với `mvn test-compile`.

## Ngoài phạm vi

- Không thay đổi DTO, Controller hoặc Service của module report.
- Không thay đổi các câu query khác không chứa tham số trong mệnh đề `GROUP BY`.

## Kiểm tra

- Bạn có muốn chạy test `ReportSummaryIntegrationTest` để kiểm tra toàn bộ luồng báo cáo sau khi sửa không?
- Lệnh chạy test liên quan (nếu được yêu cầu):
  `mvn test -Dtest=ReportSummaryIntegrationTest`

## Câu hỏi còn mở

- Không còn câu hỏi tồn đọng.

## Bảng công việc

| Công việc | Trạng thái |
| --- | --- |
| Cập nhật `GROUP BY 1` trong `ReportRepository.java` (`dailyExpense`, `monthlyTrend`) | Hoàn thành |
| Biên dịch kiểm tra cú pháp (`mvn test-compile`) | Hoàn thành |
| Xác nhận chạy kiểm thử với người dùng | Chưa làm |
