# Cải tiến nghiệp vụ ví tiết kiệm (SAVINGS) trong module Wallet

## Bối cảnh

- Hệ thống đã bổ sung loại ví `WalletType.SAVINGS` để phục vụ cho việc tích lũy tài chính trong module `goal`.
- Hiện tại, module `wallet` chưa có các quy tắc nghiệp vụ đặc thù để kiểm soát và bảo vệ loại ví này:
  - Phía Client gọi `GET /wallets` nhận về DTO `WalletSumaryRes` nhưng DTO này chưa có trường `type`, dẫn đến giao diện không nhận diện được đâu là ví tiết kiệm để gắn nhãn, phân loại hoặc ẩn tính năng chi tiêu.
  - Người dùng có thể tự gọi `POST /wallets` để tạo ví loại `SAVINGS` độc lập, tạo ra các ví tiết kiệm mồ côi không gắn với bất kỳ mục tiêu tài chính nào.
  - Người dùng có thể vô tình xóa ví `SAVINGS` từ màn hình Quản lý Ví trong khi mục tiêu tiết kiệm liên kết vẫn đang hoạt động (`in_progress`), làm mất liên kết ví của mục tiêu.

## Quyết định đã chốt

- **Bổ sung `type` vào `WalletSumaryRes`:** Thêm trường `WalletType type` vào `WalletSumaryRes.java` kèm overloaded constructor 4 tham số để tương thích ngược 100% với các unit test và logic hiện có.
- **Chặn tạo thủ công ví `SAVINGS` qua API:** Người dùng thường không được tự tạo ví loại `SAVINGS` từ endpoint `POST /wallets`. Ví `SAVINGS` chỉ được tạo tự động thông qua việc tạo Mục tiêu tiết kiệm (`SavingsGoal`) có cờ `manageWithWallet = true`.
- **Bảo vệ ví khi xóa:** Chặn thao tác xóa mềm ví (`DELETE /wallets/{id}`) nếu ví đó là `SAVINGS` và đang được liên kết với một mục tiêu tiết kiệm còn đang hoạt động (`status = 'in_progress'`). Người dùng chỉ có thể xóa ví này khi xóa mục tiêu kèm cờ `delete_wallet = true` từ module `goal`.
- **Viết bộ Unit Test đầy đủ cho các nghiệp vụ mới:** Bổ sung `WalletBehavierImplTest.java` và test chặn trên Controller để đảm bảo độ tin cậy của code.
- **Commit Git là tiêu chí hoàn thành plan:** Sau khi kiểm tra biên dịch và nghiệm thu các file thay đổi, bắt buộc phải thực hiện commit Git với thông điệp chuẩn Conventional Commits trước khi đóng plan.

## 1. Các file sẽ thay đổi

### Code
- `src/main/java/com/datn/financeapp/module/wallet/dto/response/WalletSumaryRes.java`: Thêm trường `WalletType type` và constructor tương thích ngược.
- `src/main/java/com/datn/financeapp/common/exception/ErrorCode.java`: Thêm mã lỗi `WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED` và `WALLET_SAVINGS_LINKED_TO_GOAL`.
- `src/main/java/com/datn/financeapp/module/wallet/controller/WalletController.java:46`: Chặn tạo ví `SAVINGS` tại endpoint `POST /wallets`.
- `src/main/java/com/datn/financeapp/module/goal/repository/SavingsGoalRepository.java`: Bổ sung method `existsByWalletIdAndIsDeletedFalseAndStatus(UUID walletId, String status)`.
- `src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletBehavierImpl.java:124`: Kiểm tra chặn xóa ví `SAVINGS` nếu đang liên kết với mục tiêu tiết kiệm `in_progress`.

### Test
- `src/test/java/com/datn/financeapp/unitTest/wallet/WalletBehavierImplTest.java` *(file mới)*: Unit test kiểm tra logic nghiệp vụ cho ví tiết kiệm (chặn xóa ví SAVINGS khi có goal active, cho phép xóa khi không có goal active, mapping `WalletSumaryRes.type`).
- `src/test/java/com/datn/financeapp/unitTest/wallet/WalletControllerTest.java` *(file mới)*: Test kiểm tra endpoint `POST /wallets` ném lỗi khi người dùng tạo ví `SAVINGS`.

## 2. Lý do thay đổi

- **Nguyên nhân 1 (Thiếu thông tin nhận diện loại ví trên UI):** `WalletSumaryRes.java:5` chỉ chứa `id, name, icon, color` mà không có `type`. Frontend không thể nhận biết ví nào là ví tiết kiệm để hiển thị nhãn "Tiết kiệm" hoặc ẩn nút "Tạo chi tiêu" tương ứng.
- **Nguyên nhân 2 (Nguy cơ phát sinh ví tiết kiệm mồ côi):** Nếu cho phép tạo ví `SAVINGS` tự do qua `POST /wallets`, người dùng sẽ có những ví tiết kiệm không thuộc mục tiêu nào, gây nhập nhằng với luồng quản lý tích lũy của module `goal`.
- **Nguyên nhân 3 (Nguy cơ phá vỡ tính toàn vẹn của mục tiêu tiết kiệm):** Trong `WalletBehavierImpl.java:124`, hàm `softDelete` chỉ kiểm tra ví mặc định mà chưa kiểm tra liên kết với `SavingsGoal`. Xóa ví tiết kiệm khi mục tiêu đang chạy sẽ khiến mục tiêu mất ví ghi nhận.
- **Nguyên nhân 4 (Cần bộ kiểm thử tự động):** Các quy tắc mới cần có unit test để bảo vệ và ngăn chặn regression trong tương lai.

## 3. Hướng giải quyết / code xem trước

### Bước 1: Bổ sung trường `type` vào `WalletSumaryRes.java`
```java
public record WalletSumaryRes(
        UUID id,
        String name,
        WalletType type,
        String icon,
        String color
) {
    // Constructor tương thích ngược cho các unit test và logic cũ
    public WalletSumaryRes(UUID id, String name, String icon, String color) {
        this(id, name, null, icon, color);
    }
}
```

### Bước 2: Bổ sung ErrorCode vào `ErrorCode.java`
```java
    // Không cho phép tự tạo ví tiết kiệm thủ công qua API ví thông thường.
    WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Ví tiết kiệm chỉ được tạo tự động thông qua Mục tiêu tiết kiệm."),

    // Ví tiết kiệm đang liên kết với mục tiêu chưa hoàn thành.
    WALLET_SAVINGS_LINKED_TO_GOAL(HttpStatus.CONFLICT, "Ví tiết kiệm đang liên kết với mục tiêu chưa hoàn tất. Vui lòng quản lý từ mục Tiết kiệm."),
```

### Bước 3: Chặn tạo ví `SAVINGS` tại `WalletController.java`
```java
    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody WalletCreateReq req) {
        if (req.type() == WalletType.SAVINGS) {
            throw new BusinessException(
                    ErrorCode.WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED,
                    "Ví tiết kiệm chỉ được tạo tự động thông qua Mục tiêu tiết kiệm."
            );
        }
        return ApiResponse.of(walletBehavierService.create(req, false));
    }
```

### Bước 4: Thêm query kiểm tra trong `SavingsGoalRepository.java`
```java
    boolean existsByWalletIdAndIsDeletedFalseAndStatus(UUID walletId, String status);
```

### Bước 5: Kiểm tra khi xóa ví trong `WalletBehavierImpl.java`
```java
    @Override
    @Transactional
    public Void softDelete(UUID walletId, boolean isAdmin) {
        Wallet wallet = walletRepository.findByIdAndIsDeletedFalse(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        if (!isAdmin && !wallet.getUserId().equals(SecurityContextUtil.currentUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được phép xoá ví này");

        if (Boolean.TRUE.equals(wallet.getIsDefault()))
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_DELETABLE);

        // Chặn xóa ví tiết kiệm nếu đang liên kết với mục tiêu chưa hoàn thành
        if (wallet.getType() == WalletType.SAVINGS) {
            boolean hasActiveGoal = savingsGoalRepository.existsByWalletIdAndIsDeletedFalseAndStatus(walletId, "in_progress");
            if (hasActiveGoal) {
                throw new BusinessException(
                        ErrorCode.WALLET_SAVINGS_LINKED_TO_GOAL,
                        "Ví tiết kiệm đang liên kết với mục tiêu đang thực hiện. Vui lòng quản lý từ mục Tiết kiệm."
                );
            }
        }

        wallet.setIsDeleted(true);
        walletRepository.save(wallet);
        return null;
    }
```

### Bước 6: Viết Unit Test `WalletBehavierImplTest.java`
```java
@ExtendWith(MockitoExtension.class)
class WalletBehavierImplTest {
    @Mock private WalletRepository walletRepository;
    @Mock private SavingsGoalRepository savingsGoalRepository;
    @Mock private IconService iconService;
    @Mock private WalletMapper walletMapper;
    @InjectMocks private WalletBehavierImpl walletBehavierService;

    @Test
    void softDelete_savingsWalletLinkedToActiveGoal_throwsException() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(userId).type(WalletType.SAVINGS).isDefault(false).build();
        
        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));
        when(savingsGoalRepository.existsByWalletIdAndIsDeletedFalseAndStatus(walletId, "in_progress")).thenReturn(true);
        
        try (var mockedSecurity = mockStatic(SecurityContextUtil.class)) {
            mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);
            
            BusinessException ex = assertThrows(BusinessException.class, () -> walletBehavierService.softDelete(walletId, false));
            assertEquals(ErrorCode.WALLET_SAVINGS_LINKED_TO_GOAL.getCode(), ex.getCode());
        }
    }
}
```

## 4. Danh sách công việc chi tiết

1. **DTO:**
   - Cập nhật `WalletSumaryRes.java`: thêm `WalletType type` và constructor quá tải 4 tham số.
2. **Exception:**
   - Cập nhật `ErrorCode.java`: thêm `WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED` và `WALLET_SAVINGS_LINKED_TO_GOAL`.
3. **Repository:**
   - Cập nhật `SavingsGoalRepository.java`: thêm method `existsByWalletIdAndIsDeletedFalseAndStatus`.
4. **Controller & Service:**
   - Cập nhật `WalletController.java`: chặn tạo ví `SAVINGS` trực tiếp từ endpoint `POST /wallets`.
   - Cập nhật `WalletBehavierImpl.java`: inject `SavingsGoalRepository` và kiểm tra chặn xóa ví tiết kiệm đang gắn với mục tiêu `in_progress`.
5. **Kiểm thử (Unit Tests):**
   - Viết `WalletBehavierImplTest.java` kiểm thử logic chặn xóa ví tiết kiệm khi có goal `in_progress`, và cho phép xóa khi không có goal `in_progress`.
   - Viết `WalletControllerTest.java` kiểm thử chặn `POST /wallets` với loại `SAVINGS`.
6. **Biên dịch & Chạy Test:**
   - Chạy `mvn compile -DskipTests` để xác thực toàn bộ project biên dịch sạch sẽ.
   - Chạy các test vừa viết: `mvn test -Dtest=WalletBehavierImplTest,WalletControllerTest`.
7. **Git Commit (Tiêu chí nghiệm thu hoàn thành):**
   - Kiểm tra `git status` và `git diff` đối chiếu với danh sách file thay đổi.
   - Thực hiện commit Git: `feat(wallet): enhance savings wallet constraints, add type to WalletSumaryRes and add tests`.

## Ngoài phạm vi

- Chuyển đổi ví thường thành ví tiết kiệm hoặc ngược lại.
- Tự động tất toán số dư ví tiết kiệm khi mục tiêu hoàn thành (thuộc phase rút tiền).

## Kiểm tra

- Lệnh biên dịch kiểm tra:
  ```powershell
  mvn compile -DskipTests
  ```
- Lệnh chạy kiểm thử mới:
  ```powershell
  mvn test -Dtest=WalletBehavierImplTest,WalletControllerTest
  ```
- Tiêu chí nghiệm thu cuối cùng: Toàn bộ code & test mới chạy xanh (PASS), kiểm tra `git status` sạch và tạo commit git chính thức.

## Câu hỏi còn mở

- Không còn câu hỏi mở nào. Các yêu cầu đã được thống nhất cụ thể.

## Bảng công việc

| Công việc | Trạng thái | Ghi chú |
| :--- | :--- | :--- |
| Cập nhật `WalletSumaryRes.java` (thêm `WalletType type`) | Hoàn thành | Đã thêm type và constructor tương thích ngược |
| Cập nhật `ErrorCode.java` (thêm mã lỗi liên quan ví SAVINGS) | Hoàn thành | Đã thêm `WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED` & `WALLET_SAVINGS_LINKED_TO_GOAL` |
| Cập nhật `SavingsGoalRepository.java` (thêm query kiểm tra) | Hoàn thành | Đã thêm `existsByWalletIdAndIsDeletedFalseAndStatus` |
| Cập nhật `WalletController.java` (chặn tạo thủ công ví SAVINGS) | Hoàn thành | Chặn tại `POST /wallets` |
| Cập nhật `WalletBehavierImpl.java` (chặn xóa ví SAVINGS đang liên kết) | Hoàn thành | Đã inject `SavingsGoalRepository` và check trong `softDelete` |
| Viết Unit Test `WalletBehavierImplTest.java` & `WalletControllerTest.java` | Hoàn thành | 6 unit tests đầy đủ các nhánh nghiệp vụ |
| Chạy test kiểm thử (`mvn test -Dtest=...`) | Hoàn thành | `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` (PASS 100%) |
| Kiểm tra biên dịch dự án (`mvn compile`) | Hoàn thành | `BUILD SUCCESS` |
| Commit Git các thay đổi (Tiêu chí hoàn thành) | Hoàn thành | `feat(wallet): enhance savings wallet constraints, add type to WalletSumaryRes and add tests` |
