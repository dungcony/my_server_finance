# Sửa các lỗi phân hệ nhóm chung quỹ (G2 - G11)

## Bối cảnh

Tài liệu [review-05-group.md](file:///D:/projects/java/server/docs/problem/review-05-group.md) rà soát mã nguồn phân hệ nhóm chung quỹ (`module/group`) và chỉ ra 11 vấn đề:
- Lỗi **G1** (đảo ngược tham số `FundService.create`): Người dùng đã trực tiếp sửa chữ ký hàm trong `FundService.java` và `FundServiceImpl.java` thành `create(UUID groupId, UUID operatorId)` khớp với lời gọi tại `GroupServiceImpl.java:87`.
- Người dùng đã chuyển đổi entity `GroupTxn` và migration `V4__group.sql` sang dùng cột `category_code` (VARCHAR) thay cho `category_id` (UUID).
- Lỗi **G8** (Kiểm kê quỹ): Code Java trong `GroupTxnHelper.java:158` (`authInfo.isOwner() ? CONFIRMED : PENDING`) là **hoàn toàn chính xác theo đúng nghiệp vụ** (trưởng nhóm kiểm kê thì duyệt ngay, thủ quỹ kiểm kê thì chờ duyệt). Nguyên nhân lỗi 500 là do ràng buộc CSDL `ck_gt_treasurer_confirmed` trong `V4__group.sql:104` cấm `ADJUSTMENT_*` mang trạng thái `PENDING`. Vì vậy **giữ nguyên code `GroupTxnHelper.java`, chỉ sửa lại ràng buộc CSDL trong `V4__group.sql`**.
- Lỗi **G10** (Cache quyền khi thủ quỹ rời nhóm): Thành viên thường chỉ cần biết mình không phải thủ quỹ, không cần cập nhật cache của cả nhóm. Khi thủ quỹ rời và quỹ bàn giao lại cho chủ nhóm, chỉ cần xoá cache của đúng Chủ nhóm (`authCache.evictMember(groupId, ownerId)`).
- Plan này lên phương án xử lý dứt điểm các lỗi còn lại từ **G2 đến G11** nhằm đảm bảo an toàn phân quyền, tính đúng số dư quỹ/thành viên, và đồng bộ hoàn toàn với cấu trúc `category_code`.

## Quyết định đã chốt

- **G1 (Tạo nhóm):** Đã được người dùng sửa tại `FundService.java` và `FundServiceImpl.java`. Giữ nguyên lời gọi tại `GroupServiceImpl.java:87`.
- **G8 (Kiểm kê quỹ):**
  - **Code Java (`GroupTxnHelper.java:158`): GIỮ NGUYÊN**, code đã viết đúng nghiệp vụ: `authInfo.isOwner() ? GroupTxnStatus.CONFIRMED : GroupTxnStatus.PENDING`.
  - **Sửa CSDL (`V4__group.sql:104`):** Sửa ràng buộc `ck_gt_treasurer_confirmed` thành `CHECK (type != 'REFUND' OR status = 'CONFIRMED')` để CSDL cho phép giao dịch kiểm kê ở trạng thái `PENDING` khớp với code Java.
  - **Duyệt kiểm kê (trong hàm `status()` và `statuses()` của `GroupTxnUpdateImpl.java`):** Giao dịch kiểm kê quỹ (`ADJUSTMENT_*`) do thủ quỹ tạo ở trạng thái `PENDING` thì **chỉ Trưởng nhóm (`isOwner`) mới có quyền duyệt** (thủ quỹ không tự duyệt kiểm kê của chính mình). Khi duyệt xong, hệ thống cập nhật quỹ và số dư thành viên (không gửi thông báo hỏi ghi sổ cá nhân vì không phải tiền túi).
- **G10 (Evict cache khi thủ quỹ rời nhóm):** Chỉ xóa cache của đúng Chủ nhóm bằng `authCache.evictMember(event.groupId(), event.ownerId())` khi thủ quỹ rời nhóm và bàn giao quỹ. Không cần xóa cache của cả nhóm (`evictGroup`).
- **Đồng bộ `category_code` (String):**
  - Entity `GroupTxn` dùng `categoryCode` (String).
  - Tra cứu và kiểm tra tính hợp lệ của danh mục (tồn tại, chưa xóa, là nút lá) qua `CategoryService.findByCodeAndIsDeletedAndIsLeaf(code, false, true)`. Loại bỏ hoàn toàn phụ thuộc `CateCache` nội bộ của module category (tuân thủ rule 2.2).
  - Các Event liên quan (`GroupTxnConfirmedEvent`, `GroupTxnBeingRejectEvent`) và listener thông báo (`GroupTxnListener`) chuyển `categoryId` sang `categoryCode`.
  - Khi đổi giao dịch sang `CONTRIBUTION` hoặc `REFUND`: gán `txn.setCategoryCode(null)` để tuân thủ ràng buộc `ck_gt_shape`.
- **G4 & G3 (Duyệt và từ chối giao dịch):** Trạng thái duyệt chỉ chấp nhận `CONFIRMED` hoặc `REJECTED`. Chặn giá trị `PENDING` ở validation để không vi phạm constraint `ck_gt_review`. Khi từ chối (`REJECTED`), không tính delta hay cập nhật số dư.
- **G11 (`target`):** Sửa validation annotation của `target` trong `GroupCreateReq` và `GroupUpdateReq` từ `@PositiveOrZero` thành `@Positive` để đồng nhất với ràng buộc `target > 0` trong CSDL `V4__group.sql:22`.

---

## 1. Các file sẽ thay đổi

### Code
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnCreateImpl.java:162-172`: Đồng bộ gán `categoryCode` (String) sau khi kiểm tra qua `CategoryService`.
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java`:
  - Lọc theo `groupId` khi duyệt đơn trong hàm `status()` và duyệt lô trong hàm `statuses()` (G2, dòng 116, 149).
  - Chặn trạng thái `PENDING` khi duyệt trong hàm `status()` và `statuses()` (G4, dòng 122, 160).
  - Phân quyền duyệt kiểm kê: giao dịch `ADJUSTMENT_*` chỉ cho phép Trưởng nhóm (`isOwner`) duyệt trong hàm `status()` và `statuses()` (G8).
  - Tách nhánh `CONFIRMED` (tính delta, cập nhật số dư, quỹ; riêng `ADJUSTMENT_*` không gửi `GroupTxnConfirmedEvent`) và `REJECTED` (chỉ cập nhật review) trong hàm `statuses()` (G3, dòng 141-175).
  - Chỉ phát `FundBalanceChangedEvent` khi giao dịch đang `CONFIRMED` trong hàm `update()` (G5, dòng 92-100).
  - Thay `CateCache` bằng `CategoryService`, xác thực `categoryCode`, xóa `categoryCode` khi đổi sang `CONTRIBUTION`/`REFUND`, gọi `groupTxnHelper.validParticipants`, kiểm tra hạn mức `REFUND`, trả về `toDetailResponse` trong hàm `update()` (G7, dòng 47-102, 190-205).
- `src/main/java/com/datn/financeapp/module/group/repository/GroupTxnRepository.java:31`: Đảm bảo method `findByIdInAndGroupIdAndDeletedAtIsNullAndStatus` gắn `@EntityGraph(attributePaths = {"participants"})` để fetch đầy đủ danh sách người tham gia khi duyệt lô (G2).
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java:51, 60`: Bọc `balanceService.applyDelta` trong điều kiện `txn.getStatus() == GroupTxnStatus.CONFIRMED` (G6).
- `src/main/java/com/datn/financeapp/module/group/service/impl/MemberBehavierServiceImpl.java:77`: Thêm kiểm tra `isOwner()` và `inGroupActive()` trong `ownerAddMembers` (G9).
- `src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java:167`: Gọi `authCache.evictMember(event.groupId(), event.ownerId())` trong listener `onMemberLeave` khi quỹ bàn giao lại cho chủ nhóm (G10).
- `src/main/java/com/datn/financeapp/module/group/dto/request/group/GroupCreateReq.java:15`: Đổi `@PositiveOrZero` thành `@Positive` cho trường `target` (G11).
- `src/main/java/com/datn/financeapp/module/group/dto/request/group/GroupUpdateReq.java:11`: Đổi `@PositiveOrZero` thành `@Positive` cho trường `target` (G11).
- `src/main/java/com/datn/financeapp/module/group/service/impl/MemberServiceImpl.java:49`: Dùng `memberRepository.saveAndFlush(member)` để ngoại lệ nổ ngay trong try-catch của `GroupServiceImpl.java:277` (G11).
- `src/main/java/com/datn/financeapp/module/group/events/GroupTxnConfirmedEvent.java:37`: Thay `UUID categoryId` bằng `String categoryCode`.
- `src/main/java/com/datn/financeapp/module/group/events/GroupTxnBeingRejectEvent.java:29`: Thay `UUID categoryId` bằng `String categoryCode`.
- `src/main/java/com/datn/financeapp/module/notification/events/listener/GroupTxnListener.java:44`: Đổi `category_id` sang `category_code` dạng String, tránh lỗi NPE.

### CSDL (Migration)
- `src/main/resources/db/migration_v2/V4__group.sql:104`: Sửa constraint `ck_gt_treasurer_confirmed` thành `CHECK (type != 'REFUND' OR status = 'CONFIRMED')` để CSDL cho phép `ADJUSTMENT_UP` và `ADJUSTMENT_DOWN` ở trạng thái `PENDING` khớp với code Java (G8).
- `src/main/resources/db/migration_v2/V4__group.sql:108`: Sửa `category_id IS NULL` thành `category_code IS NULL` trong ràng buộc `ck_gt_shape`.

### File không đổi
- `src/main/java/com/datn/financeapp/module/group/helper/GroupTxnHelper.java:158`: **Không đổi** (code Java đã đúng nghiệp vụ: `authInfo.isOwner() ? GroupTxnStatus.CONFIRMED : GroupTxnStatus.PENDING`).
- `src/main/java/com/datn/financeapp/module/group/service/FundService.java`: Người dùng đã sửa.
- `src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java:48`: Người dùng đã sửa.
- `src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java:87`: Đã khớp với signature mới `create(groupId, operatorId)`.

### Test (nếu được yêu cầu viết/sửa test)
- `src/test/java/com/datn/financeapp/module/group/service/impl/GroupTxnMapperImplTest.java`
- `src/test/java/com/datn/financeapp/module/group/service/impl/MemberBehavierServiceImplTest.java`
- `src/test/java/com/datn/financeapp/module/group/service/impl/FundServiceImplTest.java`

---

## 2. Lý do thay đổi

- **G10 ([FundServiceImpl.java:167](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java#L167)):**
  - *Hiện tượng & Nguyên nhân:* Khi thủ quỹ rời nhóm, quỹ tự động gán lại cho chủ nhóm (`fund.setKeepperId(event.ownerId())`). Nhưng cache quyền `MemberAuthInfo` của riêng Chủ nhóm trên Redis vẫn ghi nhận thủ quỹ cũ (`fundkeeperId != ownerId`).
  - *Hậu quả:* Chủ nhóm bị mất quyền hạn thủ quỹ (kiểm kê, hoàn tiền...) trong tối đa 30 phút TTL của cache.
  - *Khắc phục:* Gọi `authCache.evictMember(event.groupId(), event.ownerId())` để chỉ xóa cache của đúng Chủ nhóm, không làm mất cache của các thành viên khác.
- **G8 ([V4__group.sql:104](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V4__group.sql#L104), [GroupTxnUpdateImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java)):**
  - *Hiện tượng & Nguyên nhân:* Code Java `GroupTxnHelper.java:158` quy định đúng: Trưởng nhóm duyệt ngay (`CONFIRMED`), thủ quỹ tạo thì chờ duyệt (`PENDING`). Nhưng CSDL migration `V4__group.sql:104` lại có check constraint `ck_gt_treasurer_confirmed` ép `ADJUSTMENT_*` bắt buộc phải là `CONFIRMED`.
  - *Hậu quả:* Khi thủ quỹ tạo kiểm kê, CSDL chặn không cho insert dòng mang trạng thái `PENDING` -> Nổ lỗi 500.
  - *Khắc phục:* Giữ nguyên code `GroupTxnHelper.java`. Sửa constraint CSDL để cho phép `PENDING`. Đồng thời trong hàm `status()` và `statuses()`, kiểm tra chỉ Trưởng nhóm (`isOwner`) mới có quyền duyệt giao dịch kiểm kê.
- **G2 ([GroupTxnUpdateImpl.java:116](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L116), [GroupTxnUpdateImpl.java:149](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L149)):**
  - *Hiện tượng & Nguyên nhân:* Query tìm giao dịch duyệt chỉ lọc theo `txnId` và `status = PENDING` mà không lọc theo `groupId`.
  - *Hậu quả:* Chủ nhóm X duyệt được giao dịch của nhóm Y, tiền của nhóm Y bị nhập sai vào quỹ nhóm X. Vi phạm quy tắc bất biến số 8 và phân quyền độc lập nhóm.
- **G3 ([GroupTxnUpdateImpl.java:141-175](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L141)):**
  - *Hiện tượng & Nguyên nhân:* Hàm `statuses()` luôn tính `totalDelta`, gọi `balanceService.applyDelta` và publish `FundBalanceChangedEvent` ngay cả khi trạng thái là `REJECTED`.
  - *Hậu quả:* Từ chối hàng loạt vẫn làm tăng/giảm quỹ và số dư của các thành viên giống hệt phê duyệt.
- **G4 ([GroupTxnUpdateImpl.java:122, 160](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L122)):**
  - *Hiện tượng & Nguyên nhân:* API duyệt nhận giá trị `status` bất kỳ. Nếu gửi `PENDING`, code vẫn gán `reviewed_by` và `reviewed_at`.
  - *Hậu quả:* Vi phạm ràng buộc CSDL `ck_gt_review` (`status = PENDING` thì `reviewed_by` và `reviewed_at` phải là `NULL`) -> Lỗi 500.
- **G5 ([GroupTxnUpdateImpl.java:92-100](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L92)):**
  - *Hiện tượng & Nguyên nhân:* Sửa giao dịch `PENDING` có chặn số dư thành viên nhưng không chặn quỹ (`FundBalanceChangedEvent` bị phát ngoài khối `if (CONFIRMED)`).
  - *Hậu quả:* Quỹ bị trừ ngay khi sửa dù chưa được duyệt. Đến lúc duyệt, toàn bộ số tiền lại bị trừ tiếp lần nữa (trừ tiền kép).
- **G6 ([GroupTxnDeleteImpl.java:51, 60](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java#L51)):**
  - *Hiện tượng & Nguyên nhân:* Xoá mềm giao dịch chỉ kiểm tra `CONFIRMED` cho quỹ, còn `balanceService.applyDelta(groupId, before, empty)` chạy vô điều kiện.
  - *Hậu quả:* Giao dịch chưa duyệt (`PENDING`/`REJECTED`) chưa từng được tính vào số dư, khi xoá lại bị trừ đi dẫn đến số dư thành viên bị âm sai lệch.
- **G7 ([GroupTxnUpdateImpl.java:47-102](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L47)):**
  - *Hiện tượng & Nguyên nhân:* Thiếu gọi `validParticipants` (lệch tổng tiền, chia người ngoài nhóm); `REFUND` chưa kiểm tra hạn mức; đổi loại sang `CONTRIBUTION`/`REFUND` không xoá danh mục vi phạm `ck_gt_shape`; dùng `cateCache.findId` sai quy tắc module (rule 2.2); hàm kết thúc bằng `return null`.
  - *Hậu quả:* Sai lệch dữ liệu chia tiền, lỗi 500 DB, và client nhận `data: null`.
- **G9 ([MemberBehavierServiceImpl.java:77](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/MemberBehavierServiceImpl.java#L77)):**
  - *Hiện tượng & Nguyên nhân:* `ownerAddMembers` chỉ kiểm tra `inGroupNotDeleted`, không kiểm tra `isOwner()` và không kiểm tra `inGroupActive()`.
  - *Hậu quả:* Thành viên thường thêm được người vào nhóm; nhóm đang lưu trữ (`ARCHIVED`) vẫn thêm được người (vi phạm quy tắc 3.7).
- **G11 ([GroupServiceImpl.java:277](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java#L277), [GroupCreateReq.java:15](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/group/dto/request/group/GroupCreateReq.java#L15), [V4__group.sql:22](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V4__group.sql#L22)):**
  - *Hiện tượng & Nguyên nhân:* `memberService.create` dùng `save` không flush nên lỗi trùng chỉ nổ lúc commit transaction (ngoài try-catch); DTO dùng `@PositiveOrZero` trong khi CSDL ràng buộc `target > 0`.
  - *Hậu quả:* Lỗi 500 thay vì trả lỗi nghiệp vụ 409 khi trùng thành viên, và lỗi 500 khi client gửi `target = 0`.

---

## 3. Hướng giải quyết / code xem trước

### Bước 1: Sửa G8 (Duyệt giao dịch kiểm kê)
- **Code Java `GroupTxnHelper.java:158`**: **GIỮ NGUYÊN**, không thay đổi:
```java
// kiểm kê: trưởng nhóm duyệt ngay, thủ quỹ chờ duyệt
case ADJUSTMENT_UP, ADJUSTMENT_DOWN ->
        authInfo.isOwner() ? GroupTxnStatus.CONFIRMED : GroupTxnStatus.PENDING;
```

- **Phân quyền duyệt giao dịch kiểm kê trong `GroupTxnUpdateImpl.java:status`**:
```java
// Kiểm kê quỹ do thủ quỹ tạo thì chỉ có Trưởng nhóm mới có quyền duyệt
if (transaction.getType() == GroupTxnType.ADJUSTMENT_UP || transaction.getType() == GroupTxnType.ADJUSTMENT_DOWN) {
    if (!auth.isOwner()) {
        throw new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED);
    }
}
```

### Bước 2: Sửa G9 (Phân quyền thêm thành viên của Owner)
Trong `MemberBehavierServiceImpl.java:77`:
```java
authCache.get(groupId, operatorId)
        .filter(MemberAuthInfo::inGroupActive)
        .filter(MemberAuthInfo::isOwner)
        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED));
```

### Bước 3: Sửa G10 (Chỉ evict cache của Chủ nhóm khi thủ quỹ rời nhóm)
Trong `FundServiceImpl.java:167`:
```java
@EventListener
@Transactional
public void onMemberLeave(MemberLeaveEvent event) {
    fundRepository.findByGroupIdForUpdate(event.groupId()).ifPresent(fund -> {
        if (fund.getKeepperId().equals(event.memberId())) {
            fund.setKeepperId(event.ownerId());
            fundRepository.save(fund);
            // Chỉ xóa cache của đúng Chủ nhóm (người vừa nhận lại quỹ)
            authCache.evictMember(event.groupId(), event.ownerId());
        }
    });
}
```

### Bước 4: Sửa G2, G3, G4 trong duyệt giao dịch (`GroupTxnUpdateImpl.java`)
- **Trong `status()` (G2, G4, G8)**:
```java
if (req.status() != GroupTxnStatus.CONFIRMED && req.status() != GroupTxnStatus.REJECTED) {
    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái phê duyệt chỉ được là CONFIRMED hoặc REJECTED");
}

GroupTxn transaction = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(req.txnId(), groupId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

if (transaction.getStatus() != GroupTxnStatus.PENDING) {
    throw new BusinessException(ErrorCode.GROUP_TXN_NOT_PENDING);
}

// Kiểm kê quỹ chỉ trưởng nhóm được duyệt
if (transaction.getType() == GroupTxnType.ADJUSTMENT_UP || transaction.getType() == GroupTxnType.ADJUSTMENT_DOWN) {
    if (!auth.isOwner()) {
        throw new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED);
    }
}

transaction.setReviewedBy(operatorId);
transaction.setReviewedAt(now);
transaction.setStatus(req.status());

if (req.status() == GroupTxnStatus.CONFIRMED) {
    balanceService.applyDelta(groupId, new MemberBalances(), BalanceCalculator.effectOf(transaction));
    applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, GroupTxnHelper.calculateDelta(transaction)));
    // Chỉ khoản chi tiêu/góp quỹ/hoàn tiền mới hỏi ghi sổ cá nhân
    if (transaction.getType() != GroupTxnType.ADJUSTMENT_UP && transaction.getType() != GroupTxnType.ADJUSTMENT_DOWN) {
        applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(transaction));
    }
}

return groupTransactionMapper.toDetailResponse(transaction);
```

- **Trong `statuses()` (G2, G3, G4, G8)**:
```java
if (req.status() != GroupTxnStatus.CONFIRMED && req.status() != GroupTxnStatus.REJECTED) {
    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái phê duyệt chỉ được là CONFIRMED hoặc REJECTED");
}

List<GroupTxn> txns = groupTxnRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(req.txnIds(), groupId, GroupTxnStatus.PENDING);

if (txns.size() != req.txnIds().size())
    throw new BusinessException(ErrorCode.GROUP_TXN_NOT_FOUD);

// Nếu trong lô có kiểm kê quỹ thì bắt buộc người duyệt phải là Trưởng nhóm
boolean hasAdjustment = txns.stream().anyMatch(t -> t.getType() == GroupTxnType.ADJUSTMENT_UP || t.getType() == GroupTxnType.ADJUSTMENT_DOWN);
if (hasAdjustment && !auth.isOwner()) {
    throw new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED);
}

if (req.status() == GroupTxnStatus.CONFIRMED) {
    long totalDelta = 0L;
    MemberBalances totalEffect = new MemberBalances();
    for (var t : txns) {
        t.setReviewedAt(now);
        t.setStatus(GroupTxnStatus.CONFIRMED);
        t.setReviewedBy(operatorId);

        totalDelta += GroupTxnHelper.calculateDelta(t);
        totalEffect = totalEffect.plus(BalanceCalculator.effectOf(t));

        if (t.getType() != GroupTxnType.ADJUSTMENT_UP && t.getType() != GroupTxnType.ADJUSTMENT_DOWN) {
            applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(t));
        }
    }
    balanceService.applyDelta(groupId, new MemberBalances(), totalEffect);
    if (totalDelta != 0) {
        applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, totalDelta));
    }
} else {
    for (var t : txns) {
        t.setReviewedAt(now);
        t.setStatus(GroupTxnStatus.REJECTED);
        t.setReviewedBy(operatorId);
    }
}
return txns.size();
```

### Bước 5: Cập nhật `categoryCode` trong Tạo và Sửa giao dịch (G5, G7)
- **Trong `GroupTxnCreateImpl.java:162`**:
```java
private void expense(GroupTxnCreateReq req, GroupTxn txn) {
    if (req.categoryCode() == null || req.categoryCode().isBlank())
        throw new BusinessException(ErrorCode.GROUP_TXN_CATEGORY_REQUIRED);

    categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

    txn.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
    txn.setCategoryCode(req.categoryCode());
}
```

- **Trong `GroupTxnUpdateImpl.java:update` (G5, G7)**:
```java
// Xác thực categoryCode qua CategoryService (thay thế CateCache)
if (req.categoryCode() != null && !req.categoryCode().isBlank()) {
    categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    txn.setCategoryCode(req.categoryCode());
}

// Nếu đổi loại sang CONTRIBUTION hoặc REFUND thì xóa categoryCode để tuân thủ check ck_gt_shape
if (txn.getType() == GroupTxnType.CONTRIBUTION || txn.getType() == GroupTxnType.REFUND) {
    txn.setCategoryCode(null);
}

// Kiểm tra người chia tiền và hạn mức nếu có thay đổi participants hoặc amount
if (req.participants() != null || req.amount() != null) {
    groupTxnHelper.validParticipants(groupId, txn.getTransactorId(), 
            req.participants() != null ? req.participants() : txn.getParticipants().stream().map(GroupTxnHelper::toReq).toList(), 
            txn.getAmount());
}

// Chỉ thay đổi số dư và quỹ khi giao dịch là CONFIRMED
if (txn.getStatus() == GroupTxnStatus.CONFIRMED) {
    balanceService.applyDelta(txn.getGroupId(), before, BalanceCalculator.effectOf(txn));
    long fundDelta = GroupTxnHelper.calculateDelta(txn) - oldDelta;
    if (fundDelta != 0) {
        applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(txn.getGroupId(), fundDelta));
    }
}

groupTxnRepository.save(txn);
return groupTransactionMapper.toDetailResponse(txn);
```

- **Trong `validTypeAndMoneySources` (`GroupTxnUpdateImpl.java`)**:
```java
boolean hasCategory = (newCateCode != null && !newCateCode.isBlank())
        || (txn.getCategoryCode() != null && !txn.getCategoryCode().isBlank());
```

### Bước 6: Cập nhật Event & Notification Listener theo `categoryCode`
- Trong `GroupTxnConfirmedEvent.java`:
```java
public record GroupTxnConfirmedEvent(\
        UUID groupTxnId,
        UUID groupId,
        GroupTxnType type,
        long amount,
        UUID transactorId,
        String categoryCode,
        Instant occurredAt,
        String note,
        Map<UUID, Long> participantShares) {

    public static GroupTxnConfirmedEvent of(GroupTxn txn) {
        Map<UUID, Long> shares = txn.getParticipants().stream()
                .collect(Collectors.toMap(GroupTxnParticipant::getUserId, GroupTxnParticipant::getShareAmount));

        return new GroupTxnConfirmedEvent(
                txn.getId(),
                txn.getGroupId(),
                txn.getType(),
                txn.getAmount(),
                txn.getTransactorId(),
                txn.getCategoryCode(),
                txn.getOccurredAt(),
                txn.getNote(),
                shares);
    }
}
```
- Trong `GroupTxnListener.java:44`:
```java
shared.put("category_code", event.categoryCode());
```

### Bước 7: Sửa G6 trong xoá giao dịch (`GroupTxnDeleteImpl.java`)
```java
// Chỉ hoàn tác quỹ và số dư thành viên khi giao dịch đã CONFIRMED
if (txn.getStatus() == GroupTxnStatus.CONFIRMED) {
    long delta = GroupTxnHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
    applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, delta));
    balanceService.applyDelta(groupId, before, new MemberBalances());
}

txn.setDeletedAt(Instant.now());
groupTxnRepository.save(txn);
```

### Bước 8: Sửa G11 (`target` và flush khi join group)
- Trong `GroupCreateReq.java:15` & `GroupUpdateReq.java:11`:
```java
@Positive(message = "Mục tiêu quỹ phải lớn hơn 0.") Long target
```
- Trong `MemberServiceImpl.java:49`: gọi `memberRepository.saveAndFlush(member)` để ngoại lệ nổ ngay trong try-catch của `GroupServiceImpl.java:277` và được bắt thành `GROUP_MEMBER_ALREADY_EXISTS`.

### ⚠️ Thay đổi nhạy cảm
- **Sửa file migration CSDL (`src/main/resources/db/migration_v2/V4__group.sql`):**
  - Dòng 104: Sửa constraint `ck_gt_treasurer_confirmed` thành `CHECK (type != 'REFUND' OR status = 'CONFIRMED')` để CSDL chấp nhận giao dịch kiểm kê quỹ ở trạng thái `PENDING`.
  - Dòng 108: Sửa lỗi chính tả `category_id IS NULL` thành `category_code IS NULL` trong check constraint `ck_gt_shape`.

---

## 4. Danh sách công việc chi tiết

1. **Chỉnh sửa phân quyền:**
   - [x] Sửa `MemberBehavierServiceImpl.java:77`: Thêm `isOwner()` và `inGroupActive()` vào `ownerAddMembers` (G9).
   - [x] Sửa `FundServiceImpl.java:167`: Thêm `authCache.evictMember(event.groupId(), event.ownerId())` vào `onMemberLeave` (G10).
2. **Chỉnh sửa duyệt, cập nhật và xoá giao dịch:**
   - [x] Sửa `GroupTxnRepository.java:31`: Thêm `@EntityGraph(attributePaths = {"participants"})` cho query duyệt lô.
   - [x] Sửa `GroupTxnUpdateImpl.java:116, 149`: Thêm điều kiện `groupId` cho duyệt đơn trong `status()` và duyệt lô trong `statuses()` (G2).
   - [x] Sửa `GroupTxnUpdateImpl.java:122, 160`: Validate chặn `PENDING` khi duyệt trong `status()` và `statuses()` (G4).
   - [x] Sửa `GroupTxnUpdateImpl.java`: Thêm kiểm tra chỉ Trưởng nhóm (`isOwner`) mới được duyệt giao dịch `ADJUSTMENT_*` trong `status()` và `statuses()` (G8).
   - [x] Sửa `GroupTxnUpdateImpl.java:141-175`: Tách nhánh `CONFIRMED` và `REJECTED` trong `statuses()` (G3).
   - [x] Sửa `GroupTxnUpdateImpl.java:92-100`: Chỉ phát `FundBalanceChangedEvent` khi `CONFIRMED` trong `update()` (G5).
   - [x] Sửa `GroupTxnUpdateImpl.java:47-102, 190-205`: Thay `CateCache` bằng `CategoryService`, cập nhật `categoryCode`, xóa mã khi chuyển loại sang `CONTRIBUTION`/`REFUND`, gọi `validParticipants`, trả về `toDetailResponse` trong `update()` (G7).
   - [x] Sửa `GroupTxnDeleteImpl.java:51, 60`: Bọc `applyDelta` trong kiểm tra `CONFIRMED` (G6).
3. **Đồng bộ `categoryCode` cho tạo giao dịch, Event và Listener:**
   - [x] Sửa `GroupTxnCreateImpl.java:162-172`: Gán `txn.setCategoryCode(...)` sau khi kiểm tra qua `CategoryService`.
   - [x] Sửa `GroupTxnConfirmedEvent.java` và `GroupTxnBeingRejectEvent.java`: Đổi `categoryId` thành `categoryCode`.
   - [x] Sửa `GroupTxnListener.java:44`: Đưa `category_code` vào payload thông báo.
   - [x] Sửa `V4__group.sql:104, 108`: Bỏ `ADJUSTMENT_*` khỏi `ck_gt_treasurer_confirmed` và đổi `category_id IS NULL` thành `category_code IS NULL` trong `ck_gt_shape`.
4. **Validation và xử lý ngoại lệ (G11):**
   - [x] Sửa `GroupCreateReq.java:15` và `GroupUpdateReq.java:11`: Đổi `@PositiveOrZero` thành `@Positive`.
   - [x] Sửa `MemberServiceImpl.java:49`: Dùng `saveAndFlush(member)` trong `create`.

---

## Ngoài phạm vi

- Lỗi G1 đã được người dùng sửa trực tiếp trong `FundService.java` và `FundServiceImpl.java`.
- File `GroupTxnHelper.java:158` giữ nguyên vì logic code Java đã đúng nghiệp vụ.
- Các module khác ngoài `module/group` (như `recurring`, `wallet`, `transaction`).
- Không drop các CHECK constraint (`ck_gt_review`, `ck_gt_shape`).

---

## Kiểm tra

- Luôn hỏi trước có viết test hay không.
- Nếu người dùng đồng ý chạy test, chạy lệnh kiểm thử các file liên quan:
```powershell
mvn test -Dtest=GroupServiceImplTest,FundServiceImplTest,MemberBehavierServiceImplTest,MemberServiceImplTest,GroupTxnMapperImplTest
```

---

## Câu hỏi còn mở

*(Hiện không còn câu hỏi mở nào).*
