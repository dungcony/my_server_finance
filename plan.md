# Cập nhật Javadoc cho các module (ngoại trừ auth, user, group)

## Bối cảnh

Người dùng yêu cầu cập nhật lại Javadoc cho tất cả các module trong server Spring Boot (ngoại trừ `auth`, `user`,
`group`) tuân thủ đúng quy chuẩn Javadoc định nghĩa tại `@.agents/java-docs.md`.

## Quyết định đã chốt

- **Phạm vi áp dụng:** Các module `budget`, `category`, `common`, `debt`, `goal`, `notification`, `recurring`, `report`,
  `scheduler`, `transaction`, `wallet` và file `App.java`.
- **Loại trừ hoàn toàn:** Các module `auth`, `user`, `group`.
- **Tuân thủ nghiêm ngặt quy tắc `@.agents/java-docs.md`:**
    - Không lặp lại code (DRY).
    - Class/Interface/Repository Javadoc mô tả rõ trách nhiệm nghiệp vụ, DB table, quy ước ngầm, locking, side-effects.
    - Record/DTO: Mô tả các `@param` trên class header với đơn vị đo (VND, ms, ISO-8601...), ràng buộc dữ liệu.
    - Interface Method: Ghi rõ contract `@param`, `@return`, `@throws`.
    - Impl `@Override` Method: Dùng `{@inheritDoc}`, không lặp lại `@param`/`@return` nếu không đổi ngữ nghĩa, chỉ bổ
      sung chi tiết cài đặt hoặc `@throws` riêng.
    - Private Method: Không viết Javadoc full tags (`@param`, `@return`). Chỉ viết 1-2 dòng giải thích "LÝ DO (Why)" nếu
      có thuật toán phức tạp.
    - Inline comment: Không bao gồm số thứ tự (`// 1.`, `// Bước 1:`...).

## 1. Các file sẽ thay đổi

### Module `budget` (16 file)

- `src/main/java/com/datn/financeapp/module/budget/controller/BudgetController.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/request/CreateBudgetRequest.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/request/UpdateBudgetRequest.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetAlertResponse.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetImpactResponse.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetSuggestionResponse.java`
- `src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetSummaryResponse.java`
- `src/main/java/com/datn/financeapp/module/budget/entity/Budget.java`
- `src/main/java/com/datn/financeapp/module/budget/mapper/BudgetMapper.java`
- `src/main/java/com/datn/financeapp/module/budget/repository/BudgetProgressRepository.java`
- `src/main/java/com/datn/financeapp/module/budget/repository/BudgetRepository.java`
- `src/main/java/com/datn/financeapp/module/budget/service/BudgetAlertListener.java`
- `src/main/java/com/datn/financeapp/module/budget/service/BudgetService.java`
- `src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetRenewalWorker.java`
- `src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetServiceImpl.java`

### Module `category` (24 file)

- `src/main/java/com/datn/financeapp/module/category/controller/CategoryController.java`
- `src/main/java/com/datn/financeapp/module/category/dto/request/CreateCategoryRequest.java`
- `src/main/java/com/datn/financeapp/module/category/dto/request/ReorderCategoriesRequest.java`
- `src/main/java/com/datn/financeapp/module/category/dto/request/ToggleWalletCategoryRequest.java`
- `src/main/java/com/datn/financeapp/module/category/dto/request/UpdateCategoryRequest.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/CategoryDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/CategoryGroupResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/CategoryRefResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/CategoryResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/IconGroupResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/IconRefResponse.java`
- `src/main/java/com/datn/financeapp/module/category/dto/response/IconResponse.java`
- `src/main/java/com/datn/financeapp/module/category/entity/Category.java`
- `src/main/java/com/datn/financeapp/module/category/entity/CategoryGroup.java`
- `src/main/java/com/datn/financeapp/module/category/entity/Icon.java`
- `src/main/java/com/datn/financeapp/module/category/entity/WalletCategorySetting.java`
- `src/main/java/com/datn/financeapp/module/category/mapper/CategoryMapper.java`
- `src/main/java/com/datn/financeapp/module/category/repository/CategoryGroupRepository.java`
- `src/main/java/com/datn/financeapp/module/category/repository/CategoryRepository.java`
- `src/main/java/com/datn/financeapp/module/category/repository/IconRepository.java`
- `src/main/java/com/datn/financeapp/module/category/repository/WalletCategorySettingRepository.java`
- `src/main/java/com/datn/financeapp/module/category/service/CategoryService.java`
- `src/main/java/com/datn/financeapp/module/category/service/impl/CategoryListener.java`
- `src/main/java/com/datn/financeapp/module/category/service/impl/CategoryServiceImpl.java`

### Module `common` (42 file)

- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`
- `src/main/java/com/datn/financeapp/common`

### Module `debt` (18 file)

- `src/main/java/com/datn/financeapp/module/debt/controller/DebtController.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/request/CreateDebtRequest.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/request/CreatePaymentRequest.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/request/UpdateDebtRequest.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/request/WriteOffRequest.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/response/CreateDebtResponse.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/response/CreatePaymentResponse.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/response/DebtDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/response/DebtListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/debt/dto/response/DebtSummaryResponse.java`
- `src/main/java/com/datn/financeapp/module/debt/entity/Debt.java`
- `src/main/java/com/datn/financeapp/module/debt/entity/DebtPayment.java`
- `src/main/java/com/datn/financeapp/module/debt/mapper/DebtMapper.java`
- `src/main/java/com/datn/financeapp/module/debt/repository/DebtPaymentRepository.java`
- `src/main/java/com/datn/financeapp/module/debt/repository/DebtRepository.java`
- `src/main/java/com/datn/financeapp/module/debt/service/DebtService.java`
- `src/main/java/com/datn/financeapp/module/debt/service/impl/DebtReminderWorker.java`
- `src/main/java/com/datn/financeapp/module/debt/service/impl/DebtServiceImpl.java`

### Module `goal` (15 file)

- `src/main/java/com/datn/financeapp/module/goal/controller/GoalController.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/request/CreateContributionRequest.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/request/CreateGoalRequest.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/request/UpdateGoalRequest.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/response/CreateContributionResponse.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/response/CreateGoalResponse.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/response/GoalDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/goal/dto/response/GoalListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/goal/entity/GoalContribution.java`
- `src/main/java/com/datn/financeapp/module/goal/entity/SavingsGoal.java`
- `src/main/java/com/datn/financeapp/module/goal/mapper/GoalMapper.java`
- `src/main/java/com/datn/financeapp/module/goal/repository/GoalContributionRepository.java`
- `src/main/java/com/datn/financeapp/module/goal/repository/SavingsGoalRepository.java`
- `src/main/java/com/datn/financeapp/module/goal/service/GoalService.java`
- `src/main/java/com/datn/financeapp/module/goal/service/impl/GoalServiceImpl.java`

### Module `notification` (8 file)

- `src/main/java/com/datn/financeapp/module/notification/controller/NotificationController.java`
- `src/main/java/com/datn/financeapp/module/notification/dto/response/NotificationListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/notification/dto/response/NotificationListResponse.java`
- `src/main/java/com/datn/financeapp/module/notification/entity/Notification.java`
- `src/main/java/com/datn/financeapp/module/notification/mapper/NotificationMapper.java`
- `src/main/java/com/datn/financeapp/module/notification/repository/NotificationRepository.java`
- `src/main/java/com/datn/financeapp/module/notification/service/NotificationService.java`
- `src/main/java/com/datn/financeapp/module/notification/service/impl/NotificationServiceImpl.java`

### Module `recurring` (17 file)

- `src/main/java/com/datn/financeapp/module/recurring/controller/RecurringController.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/request/CreateRecurringRequest.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/request/PauseRecurringRequest.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/request/UpdateRecurringRequest.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/response/RecurringDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/response/RecurringListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/recurring/dto/response/RunNowResponse.java`
- `src/main/java/com/datn/financeapp/module/recurring/entity/RecurringTransaction.java`
- `src/main/java/com/datn/financeapp/module/recurring/mapper/RecurringMapper.java`
- `src/main/java/com/datn/financeapp/module/recurring/repository/RecurringTransactionRepository.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/RecurringRunnerService.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/RecurringService.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/impl/RecurringPeriodWriter.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/impl/RecurringRunnerServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/impl/RecurringServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/recurring/service/impl/RecurringSinglePeriodWriter.java`
- `src/main/java/com/datn/financeapp/module/recurring/util/RecurringDateCalculator.java`

### Module `report` (24 file)

- `src/main/java/com/datn/financeapp/module/report/config/AsyncConfig.java`
- `src/main/java/com/datn/financeapp/module/report/controller/ReportController.java`
- `src/main/java/com/datn/financeapp/module/report/dto/request/ExportRequest.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/CategoryBreakdownResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/CategoryGroupBreakdownResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/DailyTrendResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/ExportJobResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/MonthlyTrendResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/ReportHomeResponse.java`
- `src/main/java/com/datn/financeapp/module/report/dto/response/ReportSummaryResponse.java`
- `src/main/java/com/datn/financeapp/module/report/entity/ExportJob.java`
- `src/main/java/com/datn/financeapp/module/report/export/CsvReportWriter.java`
- `src/main/java/com/datn/financeapp/module/report/export/ExportAsyncRunner.java`
- `src/main/java/com/datn/financeapp/module/report/export/ExportStatusWriter.java`
- `src/main/java/com/datn/financeapp/module/report/mapper/ReportMapper.java`
- `src/main/java/com/datn/financeapp/module/report/repository/ExportJobRepository.java`
- `src/main/java/com/datn/financeapp/module/report/repository/ReportQueryFragments.java`
- `src/main/java/com/datn/financeapp/module/report/repository/ReportRepository.java`
- `src/main/java/com/datn/financeapp/module/report/service/ExportCleanupService.java`
- `src/main/java/com/datn/financeapp/module/report/service/ExportService.java`
- `src/main/java/com/datn/financeapp/module/report/service/ReportQueryService.java`
- `src/main/java/com/datn/financeapp/module/report/service/impl/ExportCleanupServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/report/service/impl/ExportServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/report/service/impl/ReportQueryServiceImpl.java`

### Module `scheduler` (6 file)

- `src/main/java/com/datn/financeapp/module/scheduler/BudgetRenewalJob.java`
- `src/main/java/com/datn/financeapp/module/scheduler/DebtReminderJob.java`
- `src/main/java/com/datn/financeapp/module/scheduler/ExportCleanupJob.java`
- `src/main/java/com/datn/financeapp/module/scheduler/IdempotencyCleanupJob.java`
- `src/main/java/com/datn/financeapp/module/scheduler/RecurringTransactionJob.java`
- `src/main/java/com/datn/financeapp/module/scheduler/WalletReconciliationJob.java`

### Module `transaction` (28 file)

- `src/main/java/com/datn/financeapp/module/transaction/controller/TransactionController.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/request/BulkCreateTransactionRequest.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/request/CreateTransactionRequest.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/request/DuplicateTransactionRequest.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/request/TransactionFilterRequest.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/request/UpdateTransactionRequest.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/AffectedBudgetResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/BulkCreateTransactionResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/CreateTransactionResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/DeleteTransactionResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/GeneratedTransactionResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionByDateResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionListItemResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionListResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionRefResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/dto/response/TransactionSummaryResponse.java`
- `src/main/java/com/datn/financeapp/module/transaction/entity/Transaction.java`
- `src/main/java/com/datn/financeapp/module/transaction/event/TransactionRecordedEvent.java`
- `src/main/java/com/datn/financeapp/module/transaction/mapper/TransactionMapper.java`
- `src/main/java/com/datn/financeapp/module/transaction/repository/TransactionRepository.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/TransactionBulkService.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/TransactionService.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/TransactionWriteCommand.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/TransactionWriter.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/impl/TransactionBulkServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/transaction/service/impl/TransactionServiceImpl.java`

### Module `wallet` (22 file)

- `src/main/java/com/datn/financeapp/module/wallet/controller/WalletController.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/request/AdjustBalanceRequest.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/request/CreateWalletRequest.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/request/ReorderWalletsRequest.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/request/TransferRequest.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/request/UpdateWalletRequest.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/AdjustBalanceResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/ReconcileResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/TransferResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletDetailResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletRawBalanceResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletRefResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletStatsResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletSummaryResponse.java`
- `src/main/java/com/datn/financeapp/module/wallet/entity/Wallet.java`
- `src/main/java/com/datn/financeapp/module/wallet/mapper/WalletMapper.java`
- `src/main/java/com/datn/financeapp/module/wallet/repository/WalletRepository.java`
- `src/main/java/com/datn/financeapp/module/wallet/service/WalletService.java`
- `src/main/java/com/datn/financeapp/module/wallet/service/WalletTransferService.java`
- `src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletServiceImpl.java`
- `src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletTransferServiceImpl.java`

### File Root App (1 file)

- `src/main/java/com/datn/financeapp/App.java`

### File không đổi

- Tất cả các file thuộc module `auth`, `user`, `group`.

## 2. Lý do thay đổi

- Đảm bảo toàn bộ codebase ngoài 3 module loại trừ (`auth`, `user`, `group`) đều tuân thủ chuẩn Javadoc mới
  (`@.agents/java-docs.md`).
- Chuẩn hóa tài liệu API hợp đồng (contract API) ở Service Interface, loại bỏ Javadoc thừa/lặp lại tại Impl class
  (`{@inheritDoc}`), bổ sung thông tin locking, transaction propagation, side-effects (event/cache evict), và tham số dữ
  liệu ở Record/DTO.
- Loại bỏ các comment đánh số thứ tự (`// 1.`, `// 2.`, `// Bước 1:`) vi phạm quy tắc comment trong project.

## 3. Hướng giải quyết / code xem trước

### Các bước thực hiện:

1. Duyệt qua từng module trong 11 module được chỉ định.
2. Kiểm tra và bổ sung/cập nhật Javadoc cho:
    - **Interface (Repository, Service):** Thêm class Javadoc về DB table/trách nhiệm nghiệp vụ; contract method Javadoc
      (`@param`, `@return`, `@throws`).
    - **Impl classes:** Sử dụng `{@inheritDoc}` cho `@Override` methods; bổ sung `@Transactional` rules, pessimistic
      locking warning, side effects.
    - **Record / DTOs:** Thêm `@param` cho từng thuộc tính ở class header, nêu rõ đơn vị (VND, ms...), quy ước âm/dương.
    - **Utility / Component / Scheduler / Listener:** Thêm class Javadoc giải thích vai trò, trigger condition, cron
      schedule.
3. Rà soát xóa toàn bộ comment dòng đơn có đánh số thứ tự (`// 1.`, `// Bước 1:...`).

### Code xem trước minh họa Javadoc chuẩn:

```java
/**
 * Quản lý truy vấn dữ liệu cho bảng ngân sách {@code budgets}.
 * <p>
 * <b>Quy ước nghiệp vụ:</b>
 * Ngân sách áp dụng cho nhóm hoặc cá nhân theo danh mục chi tiêu cụ thể.
 * </p>
 *
 * @see Budget
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    // ...
}
```

```java
/**
 * Cập nhật số dư ví sau khi phát sinh giao dịch.
 *
 * @param walletId  ID định danh của ví cần cập nhật
 * @param amount    Số tiền biến động (Đơn vị: VND); dương là ghi có, âm là ghi nợ
 * @return Đối tượng {@link WalletResponse} chứa số dư mới
 * @throws EntityNotFoundException Nếu không tìm thấy ví với ID chỉ định
 */
WalletResponse updateBalance(UUID walletId, BigDecimal amount);
```

```java
/**
 * {@inheritDoc}
 * <p>
 * <b>Cảnh báo Locking:</b>
 * Thực hiện khóa bi quan trên bản ghi {@code Wallet} để tránh race condition khi cập nhật số dư.
 * </p>
 */
@Override
@Transactional
public WalletResponse updateBalance(UUID walletId, BigDecimal amount) {
    // ...
}
```

## Ngoài phạm vi

- Không thay đổi bất kỳ logic code hay signature phương thức nào.
- Không đụng đến các file thuộc module `auth`, `user`, `group`.

## Kiểm tra

- Chạy lệnh biên dịch kiểm tra syntax Javadoc & Java code:
  `mvn compile`
- Đảm bảo dự án biên dịch thành công không có lỗi syntax Javadoc hoặc breaking changes.

## Câu hỏi còn mở

Không có. Yêu cầu đã rõ ràng và đầy đủ.

