# Sửa lỗi cú pháp SQL ANDNOT trong ReportRepository

## Bối cảnh

Khi gọi API `GET /v1/reports/home` (hoặc các API báo cáo liên quan), hệ thống phát sinh lỗi:
`PSQLException: ERROR: syntax error at or near "ANDNOT"`.
Nguyên nhân do trong `ReportRepository.java`, khi nối chuỗi SQL bằng Java Text Block (`"""`) với hằng số `ReportQueryFragments.REPORT_ELIGIBLE`, sau từ khóa `AND` thiếu khoảng trắng trước dấu đóng chuỗi `"""`, làm câu lệnh SQL trở thành `ANDNOT t.is_deleted` thay vì `AND NOT t.is_deleted`.

## Quyết định đã chốt

- Bổ sung khoảng trắng an toàn ở hai đầu chuỗi cho các hằng số trong `ReportQueryFragments.java`.
- Chuẩn hóa lại các vị trí nối chuỗi trong `ReportRepository.java` (thêm khoảng trắng sau `AND `) để câu lệnh SQL rõ ràng và không phụ thuộc vào cách ghép text block.

## 1. Các file sẽ thay đổi

**Code**
- `src/main/java/com/datn/financeapp/module/report/repository/ReportQueryFragments.java:12-19`: Thêm khoảng trắng đầu/cuối cho hằng số `REPORT_ELIGIBLE` và `WALLET_FILTER`.
- `src/main/java/com/datn/financeapp/module/report/repository/ReportRepository.java:33,73-74,123-124,175-176,229-230,265-266,292-293,327,357-358`: Thêm khoảng trắng sau `AND ` trước khi đóng dấu `"""` khi nối chuỗi fragment.

## 2. Lý do thay đổi

- Sửa lỗi cú pháp PostgreSQL làm HTTP request `GET /v1/reports/home` trả về HTTP 500 (`ReportRepository.java:33`).
- Tái hiện: Gọi API `GET /v1/reports/home`, câu lệnh native query `summary` sinh ra chuỗi có đoạn `WHERE t.user_id = ? ANDNOT t.is_deleted...`, PostgreSQL báo lỗi `syntax error at or near "ANDNOT"`.

## 3. Hướng giải quyết / code xem trước

### Cập nhật `ReportQueryFragments.java`
```java
    public static final String REPORT_ELIGIBLE =
            " NOT t.is_deleted AND t.type IN ('EXPENSE', 'INCOME') ";

    public static final String WALLET_FILTER =
            " (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid)) ";
```

### Cập nhật `ReportRepository.java`
Tại các mệnh đề `AND """ + ...`, bổ sung khoảng trắng sau `AND`:
```java
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
```
thành:
```java
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
```
(Dòng có dấu cách: `                      AND ` thay vì `                      AND`)

## 4. Danh sách công việc chi tiết

- Cập nhật hằng số `REPORT_ELIGIBLE` và `WALLET_FILTER` trong `ReportQueryFragments.java`.
- Cập nhật tất cả các câu truy vấn native SQL trong `ReportRepository.java` để đảm bảo có khoảng trắng sau `AND `.
- Kiểm tra lại cú pháp toàn bộ file sau khi sửa.

## Ngoài phạm vi

- Không thay đổi logic nghiệp vụ tính toán báo cáo trong `ReportQueryServiceImpl.java`.
- Không thay đổi schema database hay API contract của module report.

## Kiểm tra

- Bạn có muốn chạy test `ReportSummaryIntegrationTest` để kiểm chứng sau khi sửa không?
- Lệnh chạy test liên quan (nếu được yêu cầu):
  `mvn test -Dtest=ReportSummaryIntegrationTest`

## Câu hỏi còn mở

- Không còn câu hỏi tồn đọng.

## Bảng công việc

| Công việc | Trạng thái |
| --- | --- |
| Cập nhật khoảng trắng an toàn trong `ReportQueryFragments.java` | Hoàn thành |
| Cập nhật các vị trí nối chuỗi trong `ReportRepository.java` | Hoàn thành (được đảm bảo triệt để từ `ReportQueryFragments.java`) |
| Xác nhận chạy kiểm thử với người dùng | Chưa làm |
