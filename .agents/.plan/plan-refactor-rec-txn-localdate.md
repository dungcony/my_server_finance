# Kế hoạch chuẩn hóa kiểu thời gian cho module Giao dịch định kỳ (RecTxn & RecTxnSchedule)

## 1. Bối cảnh & Hiện trạng

- Module Giao dịch định kỳ quản lý hai thực thể cốt lõi:
  1. [`RecTxn.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/entity/RecTxn.java) (Cấu hình cha): Quy định quy tắc lặp, số tiền, ví, danh mục và khoảng thời gian hiệu lực.
  2. [`RecTxnSchedule.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/entity/RecTxnSchedule.java) (Buổi chạy con): Từng lần thực thi cụ thể do scheduler sinh ra sẵn (buffer tối đa 12 buổi gối đầu).
- **Vấn đề đã xác thực trong codebase**:
  - `RecTxn` đang dùng `Instant startAt` và `Instant endAt`.
  - `RecTxnSchedule` đang dùng `Instant runAt`.
  - Trong DTO [`RecDetailRes.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/response/RecDetailRes.java) tên trường là `startDate` và `endDate`, nhưng kiểu lại là `Instant` $\rightarrow$ mâu thuẫn ngữ nghĩa.
  - Phía Client khi nhận chuỗi ISO-8601 UTC (như `2026-10-18T17:00:00Z`) dễ bị lệch ngày nếu không parse cẩn thận theo múi giờ `Asia/Ho_Chi_Minh`.
  - Khi người dùng muốn đổi ngày chạy của một buổi con, DTO [`RecTxnScheduleUpdateReq`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/request/RecTxnScheduleUpdateReq.java) bắt client phải gửi `Instant runAt` rất cồng kềnh.
  - Trong tài chính cá nhân, mọi giao dịch định kỳ (tiền nhà, tiền mạng, trả góp, lương) đều theo **Lịch ngày** (ngày 5 hàng tháng, mỗi tuần vào thứ Hai...), không có nghiệp vụ nào lặp theo GIỜ (`HOUR`).
- **Mục tiêu**: Chuẩn hóa đồng bộ 100% toàn bộ module sang **`LocalDate`** (`startDate`, `endDate`, `runDate`), chỉnh sửa trực tiếp từ CSDL migration gốc và Seed Data đến Entity, DTO và Service.

---

## 2. Nguyên tắc kiến trúc đã xác thực

1. **Sửa trực tiếp file CSDL gốc & Seed Data (Không tạo migration chắp vá):**
   - File schema gốc: [`src/main/resources/db/migration_v2/V3__wallet_transaction_recurring_recurring_scheduler_icon.sql`](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V3__wallet_transaction_recurring_recurring_scheduler_icon.sql)
   - File seed data gốc duy nhất: [`src/main/resources/db/seed/03_seed_recurring_transactions.sql`](file:///D:/projects/java/server/src/main/resources/db/seed/03_seed_recurring_transactions.sql)
2. **Đồng bộ toàn bộ sang `LocalDate`:**
   - **`RecTxn`**: `startDate DATE NOT NULL`, `endDate DATE`.
   - **`RecTxnSchedule`**: `runDate DATE NOT NULL`.
3. **Loại bỏ tần suất `HOUR` trong [`FrequencyType`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/enums/FrequencyType.java):**
   - Chỉ giữ: `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`.
4. **Logic Scheduler & Sinh giao dịch thực tế:**
   - [`RecScheduleCalculator`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/helper/RecScheduleCalculator.java) tính toán hoàn toàn trên `LocalDate` thuần túy (`current.plusDays()`, `current.plusMonths()...`), không còn phải convert qua lại `ZonedDateTime`/`Instant`.
   - Scheduler quét các buổi đến hạn: `findByStatusAndRunDateLessThanEqualAndIsDeletedFalse(PENDING, LocalDate.now(ZONE_ID))`.
   - Khi sinh giao dịch `Transaction`: gán `occurredAt = schedule.getRunDate().atStartOfDay(ZONE_ID).toInstant()`.

---

## 3. Danh sách chính xác các tệp & dòng cần chỉnh sửa

### 3.1. CSDL Migration Gốc: [`V3__wallet_transaction_recurring_recurring_scheduler_icon.sql`](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V3__wallet_transaction_recurring_recurring_scheduler_icon.sql)
* **Bảng `recurring_transactions` (Dòng 66 - 90):**
  - Đổi cột:
    ```sql
    start_date DATE NOT NULL,
    end_date   DATE,
    ```
  - Đổi constraint tần suất (bỏ `HOUR`):
    ```sql
    CONSTRAINT ck_rec_frequency CHECK (frequency IN ('DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    ```
  - Đổi constraint ngày kết thúc:
    ```sql
    CONSTRAINT ck_rec_end_date CHECK (end_date IS NULL OR end_date >= start_date)
    ```
  - Đổi comment:
    ```sql
    COMMENT ON COLUMN recurring_transactions.end_date IS 'NULL = lặp vô hạn';
    ```
* **Bảng `recurring_transaction_schedules` (Dòng 91 - 118):**
  - Đổi cột:
    ```sql
    run_date DATE NOT NULL,
    ```
  - Đổi chính xác 2 index:
    ```sql
    CREATE INDEX idx_rec_sched_user_run_date ON recurring_transaction_schedules (user_id, run_date);
    CREATE INDEX idx_rec_sched_due           ON recurring_transaction_schedules (run_date) WHERE status = 'PENDING' AND NOT is_deleted;
    ```

### 3.2. File Seed Data: [`03_seed_recurring_transactions.sql`](file:///D:/projects/java/server/src/main/resources/db/seed/03_seed_recurring_transactions.sql)
* **Phần 1: INSERT INTO `recurring_transactions` (Dòng 32-48):**
  - Đổi cột `start_at` $\rightarrow$ `start_date`
  - Đổi cột `end_at` $\rightarrow$ `end_date`
  - Đổi giá trị: `CURRENT_DATE - INTERVAL '90 days'`
* **Phần 2: INSERT INTO `recurring_transaction_schedules` (Dòng 80 & 105):**
  - Đổi cột `run_at` $\rightarrow$ `run_date`
  - Đổi giá trị buổi quá khứ (Dòng 89): `(r.start_date + (m || ' months')::INTERVAL)::DATE`
  - Đổi giá trị buổi tương lai (Dòng 114): `(r.start_date + ((3 + fut) || ' months')::INTERVAL)::DATE`

### 3.3. Enum: [`FrequencyType.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/enums/FrequencyType.java)
- Xóa bỏ `HOUR`, danh sách enum chỉ còn:
  ```java
  public enum FrequencyType {
      DAY,
      WEEK,
      MONTH,
      QUARTER,
      YEAR;
  }
  ```

### 3.4. Entities
1. **[`RecTxn.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/entity/RecTxn.java):**
   ```java
   @Column(name = "start_date", nullable = false)
   private LocalDate startDate;

   @Column(name = "end_date")
   private LocalDate endDate;
   ```
2. **[`RecTxnSchedule.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/entity/RecTxnSchedule.java):**
   ```java
   @Column(name = "run_date", nullable = false)
   private LocalDate runDate;
   ```

### 3.5. DTOs
1. **[`RecCreateReq.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/request/RecCreateReq.java):**
   ```java
   @NotNull(message = "Ngày bắt đầu không được để trống.")
   @FutureOrPresent(message = "Ngày bắt đầu không được ở quá khứ.")
   LocalDate startDate,

   @Future(message = "Ngày kết thúc phải ở tương lai.")
   LocalDate endDate,
   ```
2. **[`RecUpdateReq.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/request/RecUpdateReq.java):**
   ```java
   @Future(message = "Ngày kết thúc phải ở tương lai.")
   LocalDate endDate,
   ```
3. **[`RecDetailRes.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/response/RecDetailRes.java):**
   ```java
   LocalDate startDate,
   LocalDate endDate,
   ```
4. **[`RecTxnScheduleRes.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/response/RecTxnScheduleRes.java):**
   ```java
   LocalDate runDate,
   ```
5. **[`RecTxnScheduleUpdateReq.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/request/RecTxnScheduleUpdateReq.java):**
   ```java
   LocalDate runDate,
   ```
6. **[`RecTxnScheduleCreateReq.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/dto/request/RecTxnScheduleCreateReq.java):**
   ```java
   LocalDate runDate,
   ```

### 3.6. Mappers
1. **[`RecurringMapper.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/mapper/RecurringMapper.java):**
   - Xóa bỏ `@Mapping(target = "startDate", source = "recTxn.startAt")` và `endDate` vì lúc này Entity và DTO đã cùng tên `startDate`/`endDate`, MapStruct tự động map 1-1.
   - Phương thức `toEntity`: MapStruct tự động map `startDate` và `endDate` từ `req`.
2. **[`RecTxnScheduleMapper.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/mapper/RecTxnScheduleMapper.java):**
   - Tự động map `runDate`.

### 3.7. Helper & Calculator: [`RecScheduleCalculator.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/helper/RecScheduleCalculator.java)
- Chuyển hoàn toàn sang tính toán trên `LocalDate`:
  ```java
  public LocalDate nextOccurrence(LocalDate current, FrequencyType frequency, int interval) {
      return switch (frequency) {
          case DAY -> current.plusDays(interval);
          case WEEK -> current.plusWeeks(interval);
          case MONTH -> current.plusMonths(interval);
          case QUARTER -> current.plusMonths(interval * 3L);
          case YEAR -> current.plusYears(interval);
      };
  }
  ```

### 3.8. Repository: [`RecTxnScheduleRepository.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/repository/RecTxnScheduleRepository.java)
- Cập nhật các phương thức và câu query JPQL:
  ```java
  List<RecTxnSchedule> findByStatusAndRunDateLessThanEqualAndIsDeletedFalse(
          RecTxnScheduleStatus status, LocalDate today);

  Optional<RecTxnSchedule> findTopByRecTxnIdAndIsDeletedFalseOrderByRunDateDesc(UUID recId);
  ```
- Cập nhật câu query native/JPQL (Dòng 84 & 102): đổi `s.runAt > CURRENT_TIMESTAMP` thành `s.runDate > CURRENT_DATE`.

### 3.9. Services
1. **[`RecTxnScheduleImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnScheduleImpl.java):**
   - `buildSchedules(RecTxn recTxn, LocalDate from)`:
     ```java
     LocalDate current = from;
     int count = 0;
     while (count < MAX_BUFFER) {
         if (recTxn.getEndDate() != null && current.isAfter(recTxn.getEndDate())) {
             break;
         }
         RecTxnSchedule schedule = RecTxnSchedule.builder()
                 .runDate(current)
                 .amount(recTxn.getAmount())
                 .status(RecTxnScheduleStatus.PENDING)
                 .userId(recTxn.getUserId())
                 .recTxn(recTxn)
                 .build();
         schedule.setId(UUID.randomUUID());
         schedule.setIsDeleted(false);
         schedules.add(schedule);

         current = scheduleCalculator.nextOccurrence(current, recTxn.getFrequency(), recTxn.getInterval());
         count++;
     }
     ```
   - `syncSchedules`:
     ```java
     LocalDate from = scheduleRepository.findTopByRecTxnIdAndIsDeletedFalseOrderByRunDateDesc(recTxn.getId())
             .map(latest -> scheduleCalculator.nextOccurrence(latest.getRunDate(), recTxn.getFrequency(), recTxn.getInterval()))
             .orElse(recTxn.getStartDate());
     ```
   - `processDueSchedules`:
     ```java
     List<RecTxnSchedule> dueList = scheduleRepository.findByStatusAndRunDateLessThanEqualAndIsDeletedFalse(
             RecTxnScheduleStatus.PENDING,
             LocalDate.now(RecScheduleCalculator.ZONE_ID)
     );
     ```
2. **[`RecScheduleProcessor.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecScheduleProcessor.java):**
   - Khi tạo giao dịch thực tế `Transaction`:
     ```java
     Instant occurredAt = schedule.getRunDate()
                                  .atStartOfDay(RecScheduleCalculator.ZONE_ID)
                                  .toInstant();
     Transaction txn = Transaction.builder()
             ...
             .occurredAt(occurredAt)
             .build();
     ```
   - Khi duy trì buffer gối đầu:
     ```java
     LocalDate nextRun = scheduleCalculator.nextOccurrence(latest.getRunDate(), rec.getFrequency(), rec.getInterval());
     if (rec.getEndDate() == null || !nextRun.isAfter(rec.getEndDate())) {
         // Tạo RecTxnSchedule mới với runDate(nextRun)
     } else if (pendingCount == 0) {
         rec.setIsEnabled(false);
         recTxnRepository.save(rec);
     }
     ```
3. **[`RecTxnBehavierImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnBehavierImpl.java):**
   - Trong `update`: `if (req.endDate() != null) rec.setEndDate(req.endDate());`
   - Trong `isScheduleChanged`:
     ```java
     private boolean isScheduleChanged(RecTxn rec, RecUpdateReq req) {
         return (req.frequency() != null && req.frequency() != rec.getFrequency())
                 || (req.interval() != null && !req.interval().equals(rec.getInterval()))
                 || (req.endDate() != null && !req.endDate().equals(rec.getEndDate()));
     }
     ```
4. **[`RecTxnScheduleBehavierImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnScheduleBehavierImpl.java):**
   - Trong `update`:
     ```java
     if (req.runDate() != null) {
         schedule.setRunDate(req.runDate());
     }
     ```

---

## 4. Kế hoạch các bước thực hiện (Task Checklist)

| # | Hạng mục | Chi tiết thực hiện | File liên quan |
|---|---|---|---|
| **1** | **CSDL Migration Gốc** | Sửa `start_date DATE`, `end_date DATE`, `run_date DATE`, bỏ `HOUR` và sửa 2 index | `V3__wallet_transaction_recurring_recurring_scheduler_icon.sql` |
| **2** | **Seed Data Gốc** | Sửa `start_date`, `end_date`, `run_date` và logic tính ngày | `03_seed_recurring_transactions.sql` |
| **3** | **Enum FrequencyType** | Loại bỏ `HOUR` | `FrequencyType.java` |
| **4** | **Refactor Entities** | `RecTxn` (`startDate`, `endDate`) và `RecTxnSchedule` (`runDate`) | `RecTxn.java`, `RecTxnSchedule.java` |
| **5** | **Refactor DTOs** | Chuyển `RecCreateReq`, `RecUpdateReq`, `RecDetailRes`, `RecTxnScheduleRes`, `RecTxnScheduleUpdateReq`, `RecTxnScheduleCreateReq` sang `LocalDate` | Các file DTO trong `transaction/dto` |
| **6** | **Cập nhật Mappers** | `RecurringMapper` và `RecTxnScheduleMapper` | `RecurringMapper.java`, `RecTxnScheduleMapper.java` |
| **7** | **Cập nhật Calculator** | `RecScheduleCalculator.nextOccurrence` sang `LocalDate` | `RecScheduleCalculator.java` |
| **8** | **Cập nhật Repository** | `RecTxnScheduleRepository` đổi method và query từ `runAt` sang `runDate` | `RecTxnScheduleRepository.java` |
| **9** | **Cập nhật Services** | `RecTxnScheduleImpl`, `RecScheduleProcessor`, `RecTxnBehavierImpl`, `RecTxnScheduleBehavierImpl` | Các service trong `transaction/service/impl` |
| **10** | **Kiểm tra biên dịch** | Chạy `mvn compile -DskipTests` xác nhận build thành công 100% | Toàn bộ project |
