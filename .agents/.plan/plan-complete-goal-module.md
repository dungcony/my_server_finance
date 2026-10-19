# Hoàn thiện và cải tiến module Goal (Mục tiêu tiết kiệm)

## Bối cảnh

- Module `goal` quản lý mục tiêu tích lũy tài chính (`savings_goals`) và các đợt nạp tiền (`goal_contributions`).
- Hiện tại module này đang bị loại trừ khỏi quá trình build trong `pom.xml:236` (`<exclude>com/datn/financeapp/module/goal/**</exclude>`) do:
  - Dự án đã chuyển toàn bộ sang `src/main/resources/db/migration_v2`, nhưng bảng `savings_goals` và `goal_contributions` chưa có script migration tương ứng trong thư mục này.
  - `GoalServiceImpl.java:55` vẫn phụ thuộc vào `TransactionWriter` (class cũ đã bị xóa khi tái cấu trúc module `transaction`).
- Người dùng đã bổ sung loại ví `SAVINGS` vào `WalletType.java:8`. Cần hoàn thiện cơ chế tự sinh ví `SAVINGS` khi tạo mục tiêu có quản lý ví trong app, đồng thời thiết lập các ràng buộc bảo vệ ví tiết kiệm không bị tiêu lạm vào các khoản chi tiêu thông thường.
- `GoalController.java` hiện chỉ phục vụ User cá nhân, hoàn toàn thiếu các endpoint quản trị dành cho **Admin** (xem toàn bộ mục tiêu của hệ thống, xem chi tiết mục tiêu của user bất kỳ, xóa mục tiêu vi phạm) tương tự như module `wallet` và `transaction`.

## Quyết định đã chốt

- **Đã có sẵn loại ví:** `WalletType.SAVINGS` đã được thêm vào enum `WalletType.java`.
- **Cơ chế tự động tạo ví:** Khi gọi `POST /api/v1/goals` với `manageWithWallet = true`, hệ thống sẽ tự động tạo một ví mới loại `SAVINGS` với tên `[Tiết kiệm] <Tên mục tiêu>`, số dư ban đầu 0đ và gán ID vào `SavingsGoal.walletId`.
- **Bảo vệ ví SAVINGS:** Cấm sử dụng ví loại `SAVINGS` làm ví thanh toán cho giao dịch `EXPENSE` trong module `transaction` (`ErrorCode.WALLET_SAVINGS_CANNOT_SPEND`).
- **Ràng buộc nạp tiền mục tiêu:** Khi nạp tiền (`createTransaction = true`), ví nguồn (`sourceWalletId`) không được là loại `SAVINGS` và số dư ví nguồn phải đủ (`sourceWallet.balance >= amount`).
- **Giao dịch nạp tiền:** Được ghi nhận dưới dạng `transfer` từ `sourceWalletId` sang `goal.walletId` thông qua `TxnBehavierService` (không làm thay đổi tổng tài sản, không tính vào báo cáo chi tiêu).
- **Bộ API Quản trị dành cho Admin (Admin Endpoints):**
  - Bổ sung `Resource.GOAL("goal")` và các quyền `GOAL_READ`, `GOAL_DELETE`, `GOAL_UPDATE` vào `PermissionName.java`.
  - Bổ sung các endpoint Admin vào `GoalController.java`:
    - `GET /goals/admin/all`: Lấy danh sách mục tiêu toàn hệ thống (hỗ trợ phân trang `Pageable`, filter theo `user_id`, `status`).
    - `GET /goals/admin/detail/{id}`: Xem chi tiết mục tiêu và lịch sử đóng góp của bất kỳ người dùng nào (`isAdmin = true`).
    - `DELETE /goals/admin/{id}`: Admin xóa mục tiêu (kèm tùy chọn `revert_transactions` và `delete_wallet`).
  - Bảo vệ các endpoint Admin bằng `@PreAuthorize("hasAuthority(...)")`.
- **Cải tiến Controller User & Xóa mục tiêu:**
  - `GoalController.java:95`: Bổ sung tham số `delete_wallet` (boolean, mặc định `false`) vào `DELETE /goals/{id}` để cho phép tùy chọn xóa mềm ví `SAVINGS` liên kết khi xóa mục tiêu (tránh ví bị mồ côi).
  - Cập nhật Javadoc cho toàn bộ các endpoint trong `GoalController`.
- **Vị trí Migration:** Tạo file `src/main/resources/db/migration_v2/V14__create_savings_goals.sql`.

## 1. Các file sẽ thay đổi

### CSDL (Migration)
- `src/main/resources/db/migration_v2/V14__create_savings_goals.sql` *(file mới)*: Tạo bảng `savings_goals`, `goal_contributions`, cập nhật ràng buộc `wallets.type` hỗ trợ `SAVINGS`, và tạo trigger `trg_goal_contributions_sync`.

### Cấu hình build
- `pom.xml`: Bỏ `<exclude>com/datn/financeapp/module/goal/**</exclude>` và `<testExclude>com/datn/financeapp/module/goal/**</testExclude>`.

### Common & Enums & Exception
- `src/main/java/com/datn/financeapp/module/user/enums/PermissionName.java`: Bổ sung `Resource.GOAL("goal")` và các quyền `GOAL_READ`, `GOAL_DELETE`, `GOAL_UPDATE`.
- `src/main/java/com/datn/financeapp/common/exception/ErrorCode.java`: Thêm mã lỗi `WALLET_SAVINGS_CANNOT_SPEND`, `INVALID_SOURCE_WALLET`, sử dụng `WALLET_BALANCE_INSUFFICIENT`.

### DTO & Request & Response
- `src/main/java/com/datn/financeapp/module/goal/dto/request/CreateGoalRequest.java`: Thêm trường `Boolean manageWithWallet`.
- `src/main/java/com/datn/financeapp/module/goal/dto/request/GoalFilterRequest.java` *(file mới)*: DTO lọc cho Admin (`userId`, `status`, `name`).

### Controller
- `src/main/java/com/datn/financeapp/module/goal/controller/GoalController.java`:
  - Bổ sung các endpoint Admin:
    - `@GetMapping("/admin/all")`: Lấy danh sách mục tiêu hệ thống (phân trang).
    - `@GetMapping("/admin/detail/{id}")`: Xem chi tiết mục tiêu của user bất kỳ.
    - `@DeleteMapping("/admin/{id}")`: Admin xóa mục tiêu.
  - Cập nhật endpoint `DELETE /goals/{id}`: Thêm `@RequestParam(name = "delete_wallet", required = false, defaultValue = "false") boolean deleteWallet`.
  - Cập nhật Javadoc và `@PreAuthorize`.

### Service & Implementation
- `src/main/java/com/datn/financeapp/module/goal/service/GoalService.java`:
  - Bổ sung các phương thức: `adminList(GoalFilterRequest filter, Pageable pageable)`, `adminDetail(UUID goalId)`, `adminDelete(UUID goalId, boolean revertTransactions, boolean deleteWallet)`.
  - Cập nhật chữ ký `delete(UUID userId, UUID goalId, boolean revertTransactions, boolean deleteWallet)`.
- `src/main/java/com/datn/financeapp/module/goal/service/impl/GoalServiceImpl.java`: 
  - Xóa bỏ phụ thuộc vào `TransactionWriter`.
  - Tích hợp `TxnBehavierService` để tạo giao dịch chuyển tiền khi nạp mục tiêu.
  - Tích hợp `WalletBehavierService` để tự động tạo ví `SAVINGS` khi `manageWithWallet = true`.
  - Triển khai logic cho các phương thức Admin (`adminList`, `adminDetail`, `adminDelete`).
  - Triển khai xử lý `deleteWallet` trong hàm `delete(...)` (gọi `walletBehavierService.softDelete` nếu được yêu cầu).
  - Bổ sung validate ví nguồn (không được là `SAVINGS`, đủ số dư).
- `src/main/java/com/datn/financeapp/module/goal/repository/SavingsGoalRepository.java`:
  - Thêm phương thức tìm kiếm phân trang cho Admin (bằng native query).
- `src/main/java/com/datn/financeapp/module/transaction/service/impl/TxnBehavierImpl.java`:
  - Thêm kiểm tra: nếu `type == TxnType.EXPENSE` và `wallet.type == WalletType.SAVINGS` thì ném `BusinessException(ErrorCode.WALLET_SAVINGS_CANNOT_SPEND)`.

### Tài liệu thiết kế
- `docs/design/goal/plan-cai-tien-goal-va-wallet.md`: Đồng bộ với migration `V14`, `WalletType.SAVINGS`, tham số `delete_wallet` và các endpoint Admin.

## 2. Lý do thay đổi

- **Nguyên nhân 1 (Lỗi build pom.xml):** `GoalServiceImpl.java:55` gọi `TransactionWriter` đã bị xóa trong đợt refactor module transaction, khiến module goal phải bị exclude trong `pom.xml`. Cần chuyển sang sử dụng `TxnBehavierService`.
- **Nguyên nhân 2 (Thiếu schema trong migration_v2):** CSDL chính của app đã chuyển sang `classpath:db/migration_v2` (`application-dev.yml:27`), nhưng chưa có file tạo bảng cho `savings_goals` và `goal_contributions`, dẫn đến `Hibernate validate` sẽ báo lỗi thiếu bảng khi bật lại module.
- **Nguyên nhân 3 (Nhập nhằn tiền tiết kiệm):** Hiện tại ví tiết kiệm chưa có cơ chế bảo vệ, người dùng có thể vô tình chọn ví tiết kiệm để chi tiêu ăn uống, mua sắm. Cần có loại ví `SAVINGS` và chặn chi tiêu thông thường để bảo toàn số tiền tích lũy.
- **Nguyên nhân 4 (Thiếu tính năng Quản trị hệ thống):** Các module khác như `wallet`, `transaction`, `recurring` đều có nhóm API `/admin` để phục vụ ban quản trị giám sát và xử lý vi phạm. Module `goal` hiện chưa có, cần bổ sung để đảm bảo tính đồng bộ kiến trúc toàn dự án.
- **Nguyên nhân 5 (Tránh ví mồ côi):** Khi người dùng xóa mục tiêu đã gắn ví `SAVINGS`, nếu không cung cấp tùy chọn xóa ví (`delete_wallet`), ví tiết kiệm đó sẽ bị bỏ lại trong danh sách ví mà không còn liên kết với mục tiêu nào, gây rác dữ liệu trên UI.

## 3. Hướng giải quyết / code xem trước

### Bước 1: Migration CSDL `V14__create_savings_goals.sql`
```sql
-- Cập nhật ràng buộc loại ví trong wallets
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS ck_wal_type;
ALTER TABLE wallets ADD CONSTRAINT ck_wal_type 
    CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'LOAN', 'SAVINGS'));

-- Tạo bảng savings_goals
CREATE TABLE savings_goals (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    wallet_id     UUID,
    name          VARCHAR(100) NOT NULL,
    target_amount BIGINT       NOT NULL,
    saved_amount  BIGINT       NOT NULL DEFAULT 0,
    target_date   DATE,
    icon_id       UUID,
    status        VARCHAR(20)  NOT NULL DEFAULT 'in_progress',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_goal_user   FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_icon   FOREIGN KEY (icon_id)   REFERENCES icons   (id) ON DELETE SET NULL,
    CONSTRAINT ck_goal_target CHECK (target_amount > 0),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0),
    CONSTRAINT ck_goal_status CHECK (status IN ('in_progress', 'completed', 'cancelled'))
);

CREATE INDEX idx_goals_user_status ON savings_goals (user_id, status) WHERE NOT is_deleted;

-- Tạo bảng goal_contributions
CREATE TABLE goal_contributions (
    id               UUID        PRIMARY KEY,
    goal_id          UUID        NOT NULL,
    transaction_id   UUID,
    amount           BIGINT      NOT NULL,
    contributed_date DATE        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_gc_goal   FOREIGN KEY (goal_id)        REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_txn    FOREIGN KEY (transaction_id) REFERENCES transactions  (id) ON DELETE SET NULL,
    CONSTRAINT ck_gc_amount CHECK (amount > 0)
);

CREATE INDEX idx_gc_goal_date ON goal_contributions (goal_id, contributed_date) WHERE NOT is_deleted;

-- Trigger đồng bộ tiến độ và trạng thái
CREATE OR REPLACE FUNCTION fn_goal_contributions_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_goal_id UUID := COALESCE(NEW.goal_id, OLD.goal_id);
    v_total   BIGINT;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM goal_contributions WHERE goal_id = v_goal_id AND NOT is_deleted;

    UPDATE savings_goals
       SET saved_amount = v_total,
           status = CASE
               WHEN status = 'cancelled'        THEN 'cancelled'
               WHEN v_total >= target_amount    THEN 'completed'
               ELSE 'in_progress'
           END
     WHERE id = v_goal_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_goal_contributions_sync
    AFTER INSERT OR UPDATE OR DELETE ON goal_contributions
    FOR EACH ROW EXECUTE FUNCTION fn_goal_contributions_sync();
```

### Bước 2: Bổ sung quyền trong `PermissionName.java`
```java
// Trong enum Resource:
GOAL("goal"),

// Trong PermissionName:
GOAL_READ(Resource.GOAL, Action.READ, "Xem danh sách và chi tiết mục tiêu tiết kiệm"),
GOAL_UPDATE(Resource.GOAL, Action.UPDATE, "Cập nhật mục tiêu tiết kiệm"),
GOAL_DELETE(Resource.GOAL, Action.DELETE, "Xóa mục tiêu tiết kiệm"),
```

### Bước 3: Cập nhật Controller `GoalController.java`
```java
private static final String MANAGE_GOAL_READ = 
        "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_READ.getValue())";
private static final String MANAGE_GOAL_DELETE = 
        "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_DELETE.getValue())";

// API User xóa mục tiêu
@DeleteMapping("/{id}")
public ApiResponse<Void> delete(
        @PathVariable UUID id,
        @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
        boolean revertTransactions,
        @RequestParam(name = "delete_wallet", required = false, defaultValue = "false")
        boolean deleteWallet) {
    UUID userId = SecurityContextUtil.currentUserId();
    goalService.delete(userId, id, revertTransactions, deleteWallet);
    return ApiResponse.of(null);
}

// API Admin xem danh sách mục tiêu toàn hệ thống
@PreAuthorize(MANAGE_GOAL_READ)
@GetMapping("/admin/all")
public ApiResponse<Page<GoalListItemResponse>> adminList(
        @ModelAttribute GoalFilterRequest filter,
        Pageable pageable) {
    return ApiResponse.of(goalService.adminList(filter, pageable));
}

// API Admin xem chi tiết mục tiêu của user bất kỳ
@PreAuthorize(MANAGE_GOAL_READ)
@GetMapping("/admin/detail/{id}")
public ApiResponse<GoalDetailResponse> adminDetail(@PathVariable UUID id) {
    return ApiResponse.of(goalService.adminDetail(id));
}

// API Admin xóa mục tiêu
@PreAuthorize(MANAGE_GOAL_DELETE)
@DeleteMapping("/admin/{id}")
public ApiResponse<Void> adminDelete(
        @PathVariable UUID id,
        @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
        boolean revertTransactions,
        @RequestParam(name = "delete_wallet", required = false, defaultValue = "false")
        boolean deleteWallet) {
    goalService.adminDelete(id, revertTransactions, deleteWallet);
    return ApiResponse.of(null);
}
```

### Bước 4: Chặn chi tiêu từ ví SAVINGS trong `TxnBehavierImpl`
```java
if (req.type() == TxnType.EXPENSE) {
    WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(req.walletId(), userId, false).orElse(null);
    if (wallet != null && wallet.type() == WalletType.SAVINGS) {
        throw new BusinessException(ErrorCode.WALLET_SAVINGS_CANNOT_SPEND, "Không thể sử dụng ví tiết kiệm để chi tiêu.");
    }
}
```

## 4. Danh sách công việc chi tiết

1. **CSDL:**
   - Tạo file `src/main/resources/db/migration_v2/V14__create_savings_goals.sql`.
2. **Quyển & ErrorCode:**
   - Cập nhật `PermissionName.java`: Thêm `Resource.GOAL` và `GOAL_READ`, `GOAL_UPDATE`, `GOAL_DELETE`.
   - Cập nhật `ErrorCode.java`: Bổ sung các mã lỗi `WALLET_SAVINGS_CANNOT_SPEND`, `INVALID_SOURCE_WALLET`, sử dụng `WALLET_BALANCE_INSUFFICIENT`.
3. **DTO:**
   - Cập nhật `CreateGoalRequest.java`: Thêm `Boolean manageWithWallet`.
   - Tạo mới `GoalFilterRequest.java`: Chứa các tiêu chí lọc cho Admin.
4. **Repository:**
   - Cập nhật `SavingsGoalRepository.java`: Thêm query / method hỗ trợ phân trang cho Admin.
5. **Controller:**
   - Cập nhật `GoalController.java`: Thêm tham số `delete_wallet` cho `DELETE /goals/{id}`.
   - Thêm các endpoint Admin: `/admin/all`, `/admin/detail/{id}`, `/admin/{id}` với `@PreAuthorize`.
6. **Logic Service:**
   - Cập nhật `GoalService.java` & `GoalServiceImpl.java`: 
     - Thay `TransactionWriter` bằng `TxnBehavierService`.
     - Tự sinh ví `SAVINGS` qua `walletBehavierService.create(...)`.
     - Xử lý xóa ví liên kết khi `deleteWallet = true`.
     - Validate ví nguồn (không được là `SAVINGS`, đủ số dư).
     - Triển khai `adminList`, `adminDetail`, `adminDelete`.
   - Cập nhật `TxnBehavierImpl.java`: Thêm kiểm tra chặn ví `SAVINGS` thanh toán chi tiêu (`EXPENSE`).
7. **Build & Include:**
   - Bỏ exclude module `goal` trong `pom.xml`.
   - Biên dịch kiểm tra lỗi biên dịch (`mvn compile`).

## Ngoài phạm vi

- Tính năng rút tiền từ mục tiêu về ví nguồn (sẽ triển khai ở phase kế tiếp).
- Thay đổi liên kết ví của một mục tiêu đã có phát sinh giao dịch.

## Kiểm tra

- **Lưu ý:** Luôn hỏi người dùng trước khi chạy test, không tự ý chạy test nếu không có yêu cầu.
- Lệnh biên dịch kiểm tra cú pháp code:
  ```powershell
  mvn compile -DskipTests
  ```
- Lệnh kiểm tra riêng module Goal khi người dùng đồng ý:
  ```powershell
  mvn test -Dtest=GoalServiceImplTest
  ```

## Câu hỏi còn mở

- Không còn câu hỏi mở nào. Các quyết định kiến trúc và phân quyền đã được thống nhất đầy đủ.

## Bảng công việc

| Công việc | Trạng thái | Ghi chú |
| :--- | :--- | :--- |
| Tạo script migration `V14__create_savings_goals.sql` | Hoàn thành | Đã tạo và cấu hình trigger, seed category `tiet_kiem` |
| Cập nhật `PermissionName.java` (thêm quyền GOAL) | Hoàn thành | `GOAL_READ`, `GOAL_UPDATE`, `GOAL_DELETE` |
| Cập nhật `ErrorCode.java` | Hoàn thành | Thêm `WALLET_SAVINGS_CANNOT_SPEND`, `INVALID_SOURCE_WALLET` |
| Cập nhật DTO `CreateGoalRequest.java` | Hoàn thành | Thêm cờ `manageWithWallet` |
| Tạo DTO `GoalFilterRequest.java` | Hoàn thành | Dùng cho Admin lọc mục tiêu |
| Cập nhật `SavingsGoalRepository.java` | Hoàn thành | Hỗ trợ phân trang native query cho Admin |
| Cập nhật `GoalController.java` | Hoàn thành | Thêm param `delete_wallet` & bộ API `/admin` |
| Cập nhật `GoalService.java` interface | Hoàn thành | Thêm param `deleteWallet` & method Admin |
| Sửa `GoalServiceImpl.java` (bỏ `TransactionWriter`, tự sinh ví, xóa ví, logic Admin) | Hoàn thành | Đã tích hợp `TxnBehavierService` & `WalletBehavierService` & `WalletRepository` |
| Sửa `TxnBehavierImpl.java` (chặn ví `SAVINGS` chi tiêu) | Hoàn thành | Đã chặn `EXPENSE` cho `WalletType.SAVINGS` |
| Mở lại module `goal` trong `pom.xml` | Hoàn thành | Bỏ exclude trong `pom.xml` |
| Kiểm tra biên dịch dự án (`mvn compile`) | Hoàn thành | `mvn clean compile -DskipTests` -> BUILD SUCCESS (383 files) |
