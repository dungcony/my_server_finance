# Phân tích & Rà soát thiết kế module Budget

## Mục lục

1. [Lệch số tiền đã chi (spent) khi cập nhật giao dịch hoặc tạm dừng ngân sách](#1-lệch-số-tiền-đã-chi-spent-khi-cập-nhật-giao-dịch-hoặc-tạm-dừng-ngân-sách)
2. [Thiếu hỗ trợ Ngân sách tổng (Toàn bộ chi tiêu) do cột category_code NOT NULL](#2-thiếu-hỗ-trợ-ngân-sách-tổng-toàn-bộ-chi-tiêu-do-cột-category_code-not-null)
3. [Kẹt vòng lặp gia hạn tự động khi kỳ mới bị trùng lặp](#3-kẹt-vòng-lặp-gia-hạn-tự-động-khi-kỳ-mới-bị-trùng-lặp)
4. [Vi phạm ranh giới kiến trúc & Coupling trực tiếp giữa các module](#4-vi-phạm-ranh-giới-kiến-trúc--coupling-trực-tiếp-giữa-các-module)
5. [Vi phạm quy ước chuẩn đặt tên Service và Response phân trang](#5-vi-phạm-quy-ước-chuẩn-đặt-tên-service-và-response-phân-trang)

---

## 1. Lệch số tiền đã chi (spent) khi cập nhật giao dịch hoặc tạm dừng ngân sách

### Vấn đề

* Cột `spent` trong bảng `budgets` bị lệch số liệu (không khớp với tổng tiền thực tế của các giao dịch `EXPENSE` trong
  kỳ).

### kịch bản lỗi

* **Kịch bản 1: Bật lại ngân sách sau một thời gian tạm dừng**
    * Người dùng tạm dừng ngân sách (`isActive = false`). Trong thời gian này, các khoản chi phát sinh không được cộng
      dồn vào ngân sách.
    * Khi người dùng bật lại (`isActive = true`), hàm `changeActive` chỉ cập nhật cờ `isActive` mà không tính toán lại
      số tiền đã chi $\rightarrow$ `spent` bị thiếu toàn bộ các khoản đã chi trong thời gian tạm dừng.
* **Kịch bản 2: Giao dịch định kỳ tự động bỏ quên Event**
    * Khi tác vụ ngầm tạo giao dịch định kỳ thành công trong `RecScheduleProcessor.java`, code chỉ gọi
      `transactionRepository.save(txn)` mà không phát `TxnCreateEvent` $\rightarrow$ ngân sách hoàn toàn không nhận được
      thông báo để cộng tiền `spent`.

### phương pháp

* Trong hàm `changeActive` (khi người dùng chuyển `isActive: false -> true`), gọi hàm
  `spentCalculator.spentInPeriod(...)` để tính toán lại chính xác 100% số tiền thực chi từ các giao dịch trong kỳ và gán
  vào `budget.setSpent(...)`.
* Bổ sung phát sự kiện `TxnCreateEvent` trong `RecScheduleProcessor` sau khi lưu giao dịch định kỳ.

### Code dự kiến

1. **`BudgetServiceImpl.java` (hàm `changeActive`)**:
   ```java
   private void changeActive(Budget budget, boolean active) {
       boolean reactivating = active && !budget.getIsActive();
       if (reactivating) {
           if (budgetRepository.existsOverlapExceptId(
                   budget.getUserId(), budget.getCategoryCode(), budget.getStartDate(), budget.getEndDate(),
                   budget.getId())) {
               throw new BusinessException(ErrorCode.BUDGET_ALREADY_EXISTS);
           }
           // Tính toán lại spent từ các giao dịch thực tế trong kỳ khi bật lại
           ZoneId zone = timezoneHelper.getCurrentZone();
           long actualSpent = spentCalculator.spentInPeriod(
                   budget.getUserId(), budget.getCategoryCode(), budget.getStartDate(), budget.getEndDate(), zone);
           budget.setSpent(actualSpent);
       }
       budget.setIsActive(active);
   }
   ```
2. **`RecScheduleProcessor.java`**:
   ```java
   transactionRepository.save(txn);
   applicationEventPublisher.publishEvent(new TxnCreateEvent(txn));
   ```

---

## 2. Thiếu hỗ trợ Ngân sách tổng (Toàn bộ chi tiêu) do cột category_code NOT NULL

### Vấn đề

* Cột `category_code VARCHAR(50) NOT NULL` và có khóa ngoại ép buộc tới bảng `categories`.
* Hệ thống chỉ cho phép tạo ngân sách theo từng danh mục riêng lẻ, không thể tạo **Ngân sách tổng** (ví dụ: "Tháng này
  tổng mọi khoản chi tiêu không vượt quá 15 triệu").

### kịch bản lỗi

* Người dùng muốn kiểm soát tổng chi tiêu hàng tháng của toàn bộ các khoản sinh hoạt (không phân biệt ăn uống, mua sắm
  hay giải trí).
* Khi gửi request tạo ngân sách không có `categoryCode` (hoặc `categoryCode = null`), hệ thống báo lỗi validation
  `NotBlank` hoặc vi phạm ràng buộc `NOT NULL` của CSDL.

### phương pháp

* Đổi cột `category_code` trong bảng `budgets` thành cho phép `NULL`.
    * `category_code != NULL`: Ngân sách cho danh mục cụ thể (gồm cả các danh mục con).
    * `category_code IS NULL`: Ngân sách tổng cho toàn bộ chi tiêu của người dùng trong kỳ.
* Khi có bất kỳ giao dịch chi tiêu nào phát sinh, hệ thống sẽ cộng dồn `spent` cho cả ngân sách của danh mục tương ứng
  VÀ ngân sách tổng (nếu có).

### Code dự kiến

1. **Database Migration**:
   ```sql
   ALTER TABLE budgets ALTER COLUMN category_code DROP NOT NULL;
   ```
2. **`Budget.java` & `CreateBudgetRequest.java`**:
   ```java
   // Cho phép null categoryCode
   public record CreateBudgetRequest(
           String categoryCode, // Bỏ @NotBlank
           @NotNull @Positive Long limitAmount,
           @NotNull PeriodType periodType,
           LocalDate startDate,
           Boolean autoRenew
   ) {}
   ```
3. **`BudgetRepository.java`**:
   ```java
   @Query("""
           SELECT new com.datn.financeapp.module.budget.helper.BudgetSpent(
               b.id, b.categoryCode, b.limitAmount, b.spent, b.endDate)
           FROM Budget b
           WHERE b.userId = :userId
             AND b.isDeleted = FALSE
             AND b.isActive = TRUE
             AND :date >= b.startDate AND :date <= b.endDate
             AND (b.categoryCode IN :categoryCodes OR b.categoryCode IS NULL)
           """)
   List<BudgetSpent> findActiveByCategoryCodes(
           @Param("userId") UUID userId,
           @Param("categoryCodes") Collection<String> categoryCodes,
           @Param("date") LocalDate date);
   ```

---

## 3. Kẹt vòng lặp gia hạn tự động khi kỳ mới bị trùng lặp

### Vấn đề

* Tác vụ tự động gia hạn ngân sách (`renewOne`) khi phát hiện kỳ tiếp theo bị trùng lấn (`existsOverlapping == true`)
  thì lập tức `return;` mà không tắt cờ tự động gia hạn của ngân sách cũ.

### kịch bản lỗi

* Người dùng đã chủ động tạo thủ công một ngân sách cho tháng sau trước khi kỳ tháng này kết thúc.
* Lúc 02:00 sáng, scheduler chạy `renewExpiredBudgets`. Hàm `renewOne` tính ra kỳ mới bị trùng lặp với ngân sách người
  dùng đã tạo trước $\rightarrow$ gọi `return;`.
* Ngân sách cũ vẫn giữ nguyên `autoRenew = true`, `isActive = true`, `endDate < today`.
* Đêm tiếp theo (và tất cả các đêm sau đó), scheduler tiếp tục quét ra ngân sách cũ này và lại cố gia hạn thất
  bại $\rightarrow$ tạo thành vòng lặp vô hạn hàng đêm trong log hệ thống.

### phương pháp

* Khi không thể gia hạn do kỳ tiếp theo đã tồn tại, chủ động cập nhật `old.setAutoRenew(false)` để ngân sách cũ hoàn tất
  vòng đời, scheduler không quét lại nữa.

### Code dự kiến

* **`BudgetRenewalServiceImpl.java`**:
  ```java
  if (budgetRepository.existsOverlapping(old.getUserId(), old.getCategoryCode(), newStart, newEnd)) {
      log.warn("Ngân sách {} không thể gia hạn do kỳ tiếp theo đã tồn tại. Đã tắt autoRenew.", old.getId());
      old.setAutoRenew(false);
      budgetRepository.save(old);
      return;
  }
  ```

---

## 4. Vi phạm ranh giới kiến trúc & Coupling trực tiếp giữa các module

### Vấn đề

* Module `budget` đang truy cập trực tiếp vào repository, entity và bảng CSDL của các module khác:
    1. `BudgetCategoryHelper` inject `CategoryRepository` và dùng entity `Category`.
    2. `BudgetRepository` viết Native Query truy vấn trực tiếp bảng `transactions`.
    3. `UserTimezoneHelper` nằm trong `module/budget` nhưng lại bị module `report` import sử dụng.

### kịch bản lỗi

* Khi module `transaction` hoặc `category` thay đổi cấu trúc bảng nội bộ (đổi tên cột, tách bảng, partition dữ liệu...),
  module `budget` bị lỗi ngầm hoặc fail runtime native query do không tuân thủ ranh giới Service Public API (Rule 2.2).

### phương pháp

* Chuyển logic tính tổng chi theo kỳ và danh mục sang `TransactionService` (hoặc query service của transaction).
* Chuyển các hàm duyệt cây danh mục cha/con sang `CategoryService`.
* Di chuyển `UserTimezoneHelper` về thư mục dùng chung `common/util/`.

### Code dự kiến

1. **`TransactionService.java`**:
   ```java
   long sumExpenseInPeriod(UUID userId, Collection<String> categoryCodes, Instant startInstant, Instant endInstant);
   ```
2. **`CategoryService.java`**:
   ```java
   List<String> findAncestorCodes(String categoryCode);
   List<String> findDescendantCodes(String categoryCode);
   Map<String, CateSumaryRes> findSummaries(Collection<String> categoryCodes);
   ```
3. **`BudgetCategoryHelper.java`**:
    * Xóa bỏ phụ thuộc vào `CategoryRepository`, gọi dữ liệu thông qua `CategoryService`.
4. **Di chuyển file**:
    * Đưa `UserTimezoneHelper.java` sang package `com.datn.financeapp.common.util`.

---

## 5. Vi phạm quy ước chuẩn đặt tên Service và Response phân trang

### Vấn đề

1. Tên class triển khai Service đang đặt là `*Impl` (`BudgetImpl`, `BudgetOverviewImpl`, `BudgetRenewalImpl`,
   `BudgetSpendImpl`) thay vì chuẩn `<Domain>ServiceImpl` (Rule 2.1).
2. API danh sách Admin trong `BudgetAdminController` trả về `ApiResponse<Page<BudgetAdminRes>>` thay vì bọc qua cấu trúc
   metadata chuẩn `PageMeta` (Rule 2.4).

### phương pháp

* Đổi tên các class triển khai Service thành:
    * `BudgetImpl` $\rightarrow$ `BudgetServiceImpl`
    * `BudgetOverviewImpl` $\rightarrow$ `BudgetOverviewServiceImpl`
    * `BudgetRenewalImpl` $\rightarrow$ `BudgetRenewalServiceImpl`
    * `BudgetSpendImpl` $\rightarrow$ `BudgetSpendServiceImpl`
* Tạo DTO `BudgetAdminListRes(List<BudgetAdminRes> items, PageMeta meta)` và cập nhật Controller trả về
  `ApiResponse<BudgetAdminListRes>`.

### Code dự kiến

1. **`BudgetAdminListRes.java`**:
   ```java
   package com.datn.financeapp.module.budget.dto.response;

   import com.datn.financeapp.common.response.PageMeta;
   import java.util.List;

   public record BudgetAdminListRes(
           List<BudgetAdminRes> items,
           PageMeta meta
   ) {}
   ```
2. **`BudgetAdminController.java`**:
   ```java
   @PreAuthorize(PERM_READ)
   @GetMapping
   public ApiResponse<BudgetAdminListRes> list(
           @RequestParam(name = "user_id", required = false) UUID targetUserId,
           @RequestParam(name = "is_deleted", required = false) Boolean isDeleted,
           @RequestParam(name = "is_active", required = false) Boolean isActive,
           @RequestParam(name = "period", required = false) String period,
           Pageable pageable) {

       PeriodType periodType = period == null || period.isBlank() ? null : PeriodType.fromCode(period);
       return ApiResponse.of(budgetAdminService.list(targetUserId, isDeleted, isActive, periodType, pageable));
   }
   ```
