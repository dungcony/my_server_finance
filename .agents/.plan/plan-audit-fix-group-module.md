# Sửa các vấn đề tồn đọng trong module group

## Bối cảnh

Rà soát toàn diện (audit) trực tiếp trên toàn bộ mã nguồn của `module/group` (controllers, services, entities, DTOs, mappers, helpers), phát hiện các vấn đề nghiệp vụ, phân quyền và API contract tồn đọng:

1. **Sai phân quyền Lưu trữ / Huỷ lưu trữ / Xoá nhóm** (`GroupServiceImpl.java:188, 206, 226`): Javadoc của `GroupService` quy định rõ quyền thuộc về Trưởng nhóm (`OWNER`), nhưng code lại kiểm tra `MemberAuthInfo::isTreasurer`. Dẫn đến Thủ quỹ có thể xoá/lưu trữ nhóm, còn Trưởng nhóm thật sự lại bị `403 FORBIDDEN`.
2. **Thiếu `@RequestBody` tại endpoint duyệt giao dịch** (`GroupTxnController.java:109`): Endpoint `confirm` (`/transactions/status`) thiếu `@RequestBody`, làm Spring MVC không bind được JSON body gửi lên cho `GroupTxnUpdateStatusReq req`, dẫn đến lỗi validation 400.
3. **`generalList` chưa lọc `CONFIRMED`** (`GroupTxnGetImpl.java:79`): Javadoc trong `GroupTxnGetService.java:38` ghi rõ `generalList` là lịch sử giao dịch xem chung cho cả nhóm *(chỉ xem được confirm)*, nhưng code hiện tại truyền `statuses = null` khiến nó không lọc trạng thái. (Còn hàm `all()` vốn dành riêng cho `GET /owner/transactions`).
4. **Giao dịch Góp quỹ (`CONTRIBUTION`) bị lỗi validation và thiếu logic tự động chia đều cho cả nhóm khi tạo `EXPENSE`** (`GroupTxnCreateReq.java:46`, `GroupTxnCreateImpl.java:70, 110`): `@NotEmpty` bắt buộc `participants` khiến `CONTRIBUTION` (không có người tham gia) không thể tạo được. Đồng thời `validParticipants` bị gọi cho cả `CONTRIBUTION`. Khoản chi `EXPENSE` vắng người tham gia chưa được tự động chia đều (`splitEvenly`) cho thành viên `ACTIVE`.
5. **Trưởng nhóm không được sửa giao dịch do người khác tạo nếu không kiêm Thủ quỹ** (`MemberAuthInfo.java:61`): `isEditGroupTransaction` chỉ kiểm tra `isTreasurer()`, bỏ quên `isOwner()`.
6. **Thủ quỹ xoá được giao dịch của nhóm** (`GroupTxnDeleteImpl.java:41`): Javadoc `GroupTxnDeleteService` ghi rõ quyền chỉ thuộc về Trưởng nhóm (`OWNER`), nhưng code lại dùng `!m.isMember()` cho phép cả Thủ quỹ xoá mềm giao dịch.
7. **Cho phép đổi thủ quỹ và kiểm kê khi nhóm đã lưu trữ (ARCHIVED)** (`FundServiceImpl.java:70, 95`): Thiếu kiểm tra `inGroupActive`.
8. **Xoá nhóm chưa kiểm tra số dư thành viên bằng 0 khi bật tính thừa thiếu** (`GroupServiceImpl.java:231`): Chỉ kiểm tra `fund.getCurrentBalance() == 0` mà bỏ quên điều kiện mọi thành viên có `net_balance == 0` khi `isSettlementEnabled == true`.
9. **Thiếu `@PastOrPresent` ở `GroupTxnUpdateReq.java:37`**: `GroupTxnCreateReq` có `@PastOrPresent` cho `occurredAt`, nhưng `GroupTxnUpdateReq` chưa có, cho phép cập nhật thời gian giao dịch ở tương lai.
10. **Thiếu trường đóng góp trong response báo cáo cân đối** (`GroupBalanceItemRes.java`): Thiếu trường `totalContribution` dù trong `MemberBalances.Balance` đã có `contribution()`.

---

## Quyết định đã chốt

- **Tập trung vào logic code hiện tại, không phụ thuộc docs cũ.**
- **Lịch sử giao dịch chung:**
  - Endpoint `GET /transactions` map với `generalList`: Lọc cố định `CONFIRMED` để mọi thành viên trong nhóm đều xem được.
  - Endpoint `GET /owner/transactions` map với `all`: Dành riêng cho Owner/Thủ quỹ xem mọi trạng thái (`statuses`).
- **Quyền quản trị nhóm (Archive, Unarchive, Delete nhóm, Delete giao dịch):** Chỉ `OWNER` mới có quyền thực hiện.
- **Góp quỹ (`CONTRIBUTION`):** Không truyền người tham gia (`participants` rỗng/null). Bỏ `@NotEmpty` ở `GroupTxnCreateReq`, không gọi `validParticipants` đối với `CONTRIBUTION`.
- **Khoản chi vắng người tham gia:** Khi `EXPENSE` có `participants` rỗng hoặc null, hệ thống tự động lấy toàn bộ thành viên đang `ACTIVE` của nhóm và chia đều (`GroupTxnHelper.splitEvenly`).

---

## 1. Các file sẽ thay đổi

### Code

- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java`: Sửa kiểm tra `isOwner()` cho archive, unarchive, delete; thêm kiểm tra số dư thành viên bằng 0 khi xoá nhóm có bật tính thừa thiếu.
- `src/main/java/com/datn/financeapp/module/group/controller/GroupTxnController.java`: Thêm `@RequestBody` tại endpoint `/transactions/status` (dòng 109).
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnGetImpl.java`: Trong `generalList`, truyền `List.of(GroupTxnStatus.CONFIRMED)` vào `buildListRes`.
- `src/main/java/com/datn/financeapp/module/group/dto/request/transaction/GroupTxnCreateReq.java`: Bỏ `@NotEmpty` trên `participants`.
- `src/main/java/com/datn/financeapp/module/group/dto/request/transaction/GroupTxnUpdateReq.java`: Thêm `@PastOrPresent` cho `occurredAt`.
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnCreateImpl.java`: Phân nhánh `CONTRIBUTION` không gọi `validParticipants`, và `EXPENSE` tự động chia đều cả nhóm nếu `participants` rỗng.
- `src/main/java/com/datn/financeapp/module/group/helper/MemberAuthInfo.java`: Cập nhật `isEditGroupTransaction` cho phép `isOwner() || isTreasurer()`.
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java`: Thay `!m.isMember()` bằng `MemberAuthInfo::isOwner`.
- `src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java`: Thêm `.filter(MemberAuthInfo::inGroupActive)` cho `updateFundKeepper` và `reconcileFund`.
- `src/main/java/com/datn/financeapp/module/group/dto/response/report/GroupBalanceItemRes.java`: Bổ sung trường `Long totalContribution`.
- `src/main/java/com/datn/financeapp/module/group/service/impl/GReportServiceImpl.java`: Truyền `b.contribution()` vào `GroupBalanceItemRes`.

---

## 2. Lý do thay đổi

### 2.1. Sai quyền `archive`, `unarchive`, `delete` nhóm
- **Vị trí:** `GroupServiceImpl.java:188, 206, 226`
- **Nguyên nhân:** Dùng `MemberAuthInfo::isTreasurer` thay vì `isOwner`.
- **Hậu quả:** Thủ quỹ xoá được nhóm; Chủ nhóm thật sự nhận `403 FORBIDDEN`. Javadoc của `GroupService` quy định rõ quyền `OWNER`.

### 2.2. Thiếu `@RequestBody` ở `confirm` giao dịch
- **Vị trí:** `GroupTxnController.java:109`
- **Nguyên nhân:** `public ApiResponse<GroupTxnDetailRes> confirm(@PathVariable UUID groupId, @Valid GroupTxnUpdateStatusReq req)` thiếu `@RequestBody`.
- **Hậu quả:** Client gửi POST body JSON `{ "txnId": "...", "status": "CONFIRMED" }` nhưng Spring không bind vào `req`, `req.txnId` bị null và báo lỗi `VALIDATION_ERROR` 400.

### 2.3. `generalList` chưa lọc `CONFIRMED`
- **Vị trí:** `GroupTxnGetImpl.java:79`
- **Nguyên nhân:** Đang gọi `buildListRes(groupId, null, null, filter, pageable)` (statuses = null).
- **Hậu quả:** Trả về cả các giao dịch chưa được duyệt cho thành viên xem chung, trái với javadoc trong `GroupTxnGetService.java:38`.

### 2.4. Góp quỹ `CONTRIBUTION` bị lỗi validation và không tự chia đều cho cả nhóm
- **Vị trí:** `GroupTxnCreateReq.java:46`, `GroupTxnCreateImpl.java:110`
- **Nguyên nhân:** `@NotEmpty` bắt buộc `participants`, đồng thời `groupTxnHelper.validParticipants` được gọi chung cho mọi loại giao dịch.
- **Hậu quả:** Khi tạo `CONTRIBUTION` gửi `participants: []` hoặc null thì bị lỗi 400. Ngoài ra `EXPENSE` nếu client không truyền participants để chia cả nhóm thì bị từ chối thay vì tự động chia.

### 2.5. Trưởng nhóm không sửa được giao dịch
- **Vị trí:** `MemberAuthInfo.java:61`
- **Nguyên nhân:** `isEditGroupTransaction` chỉ check `isTreasurer()`. Nếu Owner không giữ quỹ, Owner không thể sửa khoản chi do người khác tạo.
- **Hậu quả:** Trưởng nhóm không quản lý được các giao dịch trong nhóm.

### 2.6. Thủ quỹ xoá được giao dịch
- **Vị trí:** `GroupTxnDeleteImpl.java:41`
- **Nguyên nhân:** `.filter(m -> !m.isMember())` cho phép cả Owner và Treasurer xoá giao dịch.
- **Hậu quả:** Vi phạm phân quyền ghi trong javadoc của `GroupTxnDeleteService`: Chỉ duy nhất `OWNER` có quyền xoá mềm giao dịch.

### 2.7. Đổi thủ quỹ và kiểm kê khi nhóm ARCHIVED
- **Vị trí:** `FundServiceImpl.java:70, 95`
- **Nguyên nhân:** Chỉ kiểm tra quyền `isOwner`/`isTreasurer` mà không kiểm tra `.filter(MemberAuthInfo::inGroupActive)`.
- **Hậu quả:** Nhóm đã lưu trữ vẫn có thể thay đổi người giữ quỹ hoặc kiểm kê làm thay đổi số dư quỹ.

### 2.8. Xoá nhóm chưa kiểm tra số dư thành viên bằng 0
- **Vị trí:** `GroupServiceImpl.java:231`
- **Nguyên nhân:** Chỉ kiểm tra quỹ bằng 0 và không có giao dịch chờ duyệt, thiếu kiểm tra số dư thành viên khi `group.isSettlementEnabled() == true`.
- **Hậu quả:** Nhóm còn thành viên âm/dương tiền vẫn bị xoá.

### 2.9. Thiếu `@PastOrPresent` ở `GroupTxnUpdateReq.java:37`
- **Vị trí:** `GroupTxnUpdateReq.java:37`
- **Nguyên nhân:** Thiếu annotation `@PastOrPresent` cho `occurredAt`.
- **Hậu quả:** Có thể cập nhật ngày giao dịch ở tương lai.

### 2.10. Thiếu `totalContribution` trong báo cáo cân đối
- **Vị trí:** `GroupBalanceItemRes.java`, `GReportServiceImpl.java:181`
- **Nguyên nhân:** DTO thiếu trường `totalContribution` dù trong `MemberBalances.Balance` đã có `contribution()`.
- **Hậu quả:** Client không hiển thị được số tiền đã nộp/ứng của từng thành viên.

---

## 3. Hướng giải quyết / code xem trước

### 3.1. Sửa quyền archive, unarchive, delete nhóm trong `GroupServiceImpl.java`
```java
authCache.get(groupId, operatorId)
        .filter(MemberAuthInfo::isOwner)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
```
Kiểm tra thêm số dư thành viên trong `delete`:
```java
if (Boolean.TRUE.equals(group.isSettlementEnabled())) {
    boolean hasNonZero = memberBalanceService.getBalances(groupId).members().values().stream()
            .anyMatch(b -> b.net() != 0L);
    if (hasNonZero) {
        throw new BusinessException(ErrorCode.GROUP_DELETE_BALANCE_NOT_ZERO);
    }
}
```

### 3.2. Sửa `GroupTxnController.java:109`
```java
@PostMapping("/transactions/status")
public ApiResponse<GroupTxnDetailRes> confirm(
        @PathVariable UUID groupId,
        @Valid @RequestBody GroupTxnUpdateStatusReq req) {
    return ApiResponse.of(groupTxnUpdateService.status(groupId, req));
}
```

### 3.3. Sửa `GroupTxnGetImpl.java:79` (generalList)
```java
@Override
public GroupTxnListRes generalList(UUID groupId, GroupTxnFilterReq filter, Pageable pageable) {
    UUID operatorId = SecurityContextUtil.currentUserId();

    memberAuthCache.get(groupId, operatorId)
            .filter(MemberAuthInfo::inGroupNotDeleted)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

    return buildListRes(groupId, null, List.of(GroupTxnStatus.CONFIRMED), filter, pageable);
}
```

### 3.4. Xử lý `CONTRIBUTION` và "Vắng người tham gia = Cả nhóm" trong `GroupTxnCreateImpl.java`
```java
case CONTRIBUTION -> {
    if (req.transactorId() == null)
        throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);
    if (req.moneySource() != MoneySource.PERSONAL)
        throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
    if (req.participants() != null && !req.participants().isEmpty())
        throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_NOT_ALLOWED);

    // Xác thực người đóng góp thuộc nhóm
    if (!memberService.allMemberInGroup(groupId, List.of(req.transactorId())))
        throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
}
case EXPENSE -> {
    ...
    // Nếu participants rỗng/null: chia đều cho toàn bộ thành viên ACTIVE
    if (req.participants() == null || req.participants().isEmpty()) {
        List<UUID> activeMemberIds = memberService.getActiveMemberIds(groupId);
        List<GroupTxnParticipantRes> split = GroupTxnHelper.splitEvenly(req.amount(), activeMemberIds);
        transaction.setParticipants(split.stream()
                .map(p -> GroupTxnParticipant.builder().userId(p.userId()).shareAmount(p.shareAmount()).build())
                .toList());
    } else {
        groupTxnHelper.validParticipants(groupId, req.transactorId(), req.participants(), req.amount());
        transaction.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
    }
}
```

### 3.5. Cập nhật quyền trong `MemberAuthInfo.java` và `GroupTxnDeleteImpl.java`
- Trong `MemberAuthInfo.java`:
```java
if (isOwner() || isTreasurer())
    return true;
```
- Trong `GroupTxnDeleteImpl.java`:
```java
memberAuthCache.get(groupId, operatorId)
        .filter(MemberAuthInfo::inGroupNotDeleted)
        .filter(MemberAuthInfo::isOwner)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
```

### 3.6. Cập nhật `FundServiceImpl.java`
```java
authCache.get(groupId, operatorId)
        .filter(MemberAuthInfo::inGroupActive)
        .filter(MemberAuthInfo::isOwner)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
```

### 3.7. Cập nhật `GroupBalanceItemRes.java`
```java
public record GroupBalanceItemRes(
        UUID userId,
        String fullName,
        String status,
        Long totalContribution,
        Long totalRefunded,
        Long totalShareAmount,
        Long netBalance,
        Long neededContribution) {
}
```

---

## 4. Danh sách công việc chi tiết

1. **Sửa DTO Request & Response:**
   - Cập nhật `GroupTxnCreateReq.java`: Bỏ `@NotEmpty` trên `participants`.
   - Cập nhật `GroupTxnUpdateReq.java`: Thêm `@PastOrPresent` cho `occurredAt`.
   - Cập nhật `GroupBalanceItemRes.java`: Thêm `Long totalContribution`.
   - Cập nhật `GReportServiceImpl.java`: Truyền `b.contribution()` khi khởi tạo `GroupBalanceItemRes`.
2. **Sửa Phân quyền & Quản lý Nhóm (`GroupServiceImpl`, `MemberAuthInfo`, `GroupTxnDeleteImpl`, `FundServiceImpl`):**
   - Đổi `isTreasurer` sang `isOwner` cho `archive`, `unarchive`, `delete` nhóm trong `GroupServiceImpl.java`.
   - Bổ sung kiểm tra số dư thành viên bằng 0 khi xoá nhóm có `isSettlementEnabled == true`.
   - Đổi `!m.isMember()` sang `isOwner` trong `GroupTxnDeleteImpl.java`.
   - Cập nhật `isEditGroupTransaction` trong `MemberAuthInfo.java` hỗ trợ cả `isOwner()`.
   - Thêm `inGroupActive` cho `updateFundKeepper` và `reconcileFund` trong `FundServiceImpl.java`.
3. **Sửa Logic Tạo & Truy vấn Giao dịch (`GroupTxnCreateImpl`, `GroupTxnGetImpl`, `GroupTxnController`):**
   - Xử lý `CONTRIBUTION` không gọi `validParticipants` và `EXPENSE` chia đều cả nhóm khi vắng người tham gia trong `GroupTxnCreateImpl.java`.
   - Bổ sung `@RequestBody` cho endpoint `confirm` trong `GroupTxnController.java`.
   - Trong `GroupTxnGetImpl.java:79`, `generalList` truyền `List.of(GroupTxnStatus.CONFIRMED)` để chỉ trả về giao dịch đã duyệt cho xem chung.
4. **Kiểm tra biên dịch:**
   - Chạy `mvn compile -DskipTests` để xác nhận toàn bộ mã nguồn hợp lệ không có lỗi biên dịch.

---

## Ngoài phạm vi

- Viết test suite toàn diện cho module group (cần sự xác nhận của người dùng theo `plan-rule.md`).
- Sửa đổi cấu trúc database hoặc viết file migration mới.

---

## Kiểm tra

- **Quy tắc:** Luôn hỏi trước người dùng trước khi chạy test, không tự ý chạy test suite.
- Lệnh kiểm tra biên dịch:
  ```powershell
  mvn test-compile
  ```

---

## Câu hỏi còn mở

*(Không còn)*

---

## Bảng công việc

| Công việc | Trạng thái |
|---|---|
| Sửa DTO Request & Response (`GroupTxnCreateReq`, `GroupTxnUpdateReq`, `GroupBalanceItemRes`) | Chưa làm |
| Cập nhật `GReportServiceImpl` ánh xạ `totalContribution` | Chưa làm |
| Cập nhật phân quyền `GroupServiceImpl` (archive, unarchive, delete) & kiểm tra số dư khi xoá | Chưa làm |
| Cập nhật `GroupTxnDeleteImpl` chỉ cho phép Owner xoá giao dịch | Chưa làm |
| Cập nhật `MemberAuthInfo.isEditGroupTransaction` cho phép Owner sửa giao dịch | Chưa làm |
| Thêm `inGroupActive` trong `FundServiceImpl` | Chưa làm |
| Cập nhật `GroupTxnCreateImpl` (CONTRIBUTION & chia đều cả nhóm cho EXPENSE) | Chưa làm |
| Sửa `@RequestBody` trong `GroupTxnController` | Chưa làm |
| Sửa `generalList` truyền CONFIRMED trong `GroupTxnGetImpl` | Chưa làm |
| Biên dịch và kiểm tra `mvn compile -DskipTests` | Chưa làm |
