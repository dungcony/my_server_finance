# QUY ƯỚC ĐẶT TÊN BIẾN & THAM SỐ (NAMING CONVENTIONS) — PHÂN HỆ GROUP

> **Tài liệu tham
khảo:** [rule.md](rule.md) · [api.md](api.md) · [lop-thuc-the.md](lop-thuc-the.md) · [pipeline.md](pipeline.md)

---

## 📑 MỤC LỤC

- [1. Nguyên tắc cốt lõi](#1-nguyen-tac-cot-loi)
- [2. Phân định vai trò User ID](#2-phan-dinh-vai-tro-user-id)
    - [2.1 Người gọi hàm / Người thực hiện thao tác (operatorId)](#21-nguoi-goi-ham-operatorid)
    - [2.2 Người tham gia / Người bị tác động (userId / memberId)](#22-nguoi-tham-gia-userid-memberid)
    - [2.3 Người giữ quỹ (keepperId)](#23-nguoi-giu-quy-keepperid)
- [3. Chuẩn hóa Naming tại các Service](#3-chuan-hoa-naming-tai-cac-service)
    - [3.1 GroupService](#31-groupservice)
    - [3.2 GroupMemberService](#32-groupmemberservice)
    - [3.3 GroupFundService](#33-groupfundservice)
    - [3.4 GroupTransactionService](#34-grouptransactionservice)
    - [3.5 GroupReportService](#35-groupreportservice)
    - [3.6 GroupBalanceService](#36-groupbalanceservice)
    - [3.7 GroupPermissionValidator](#37-grouppermissionvalidator)
- [4. Quy ước Naming tại Controller & DTO](#4-quy-uoc-naming-tai-controller--dto)
- [5. Bảng tra cứu nhanh Anti-patterns](#5-bang-tra-cuu-nhanh-anti-patterns)

---

# 1. Nguyên tắc cốt lõi <a id="1-nguyen-tac-cot-loi"></a>

Trong các hệ thống phân quyền phức tạp như **Nhóm tài chính (Group)**, mỗi phương thức tại tầng Service thường đồng thời
xử lý:

1. **Người đang gửi request** (Chủ nhóm, Thủ quỹ, Thành viên thao tác).
2. **Người được/bị tác động** (Thành viên được thêm, bị xóa, được phê duyệt, được nhận tiền hoàn, được chuyển giao
   quyền...).

> [!IMPORTANT]
> **Nguyên tắc vàng:** Không dùng chung tên `userId` cho cả người gọi hàm và đối tượng bị tác động trong cùng một ngữ
cảnh. Phải phân định rạch ròi giữa **Chủ thể hành động (`operatorId`)** và **Đối tượng chịu tác động (`userId` /
`memberId`)**.

---

# 2. Phân định vai trò User ID <a id="2-phan-dinh-vai-tro-user-id"></a>

| Tên biến / Tham số    | Kiểu dữ liệu | Ý nghĩa & Phạm vi áp dụng                                                                                                                                                                           | Ví dụ thực tế                                                  |
|:----------------------|:------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------|
| `operatorId`          |    `UUID`    | **Người gọi hàm / Chủ thể thực thi:** ID của user đang đăng nhập và gửi request (lấy từ Security Context qua Controller truyền xuống). Đóng vai trò kiểm tra quyền (Owner/Treasurer/Active Member). | Owner duyệt đơn, Member tự tạo chi tiêu, Member tự rời nhóm... |
| `memberId` / `userId` |    `UUID`    | **Đối tượng tham gia / Bị tác động:** ID của người dùng được thêm vào nhóm, được duyệt, bị xóa, được nhận bàn giao quyền Owner, hoặc người thụ hưởng tiền hoàn.                                 | Người được duyệt vào nhóm, Người được chuyển giao Owner...     |
| `memberIds`           | `List<UUID>` | Danh sách ID các thành viên được thêm hoặc tham gia phân bổ.                                                                                                                                        | Danh sách thêm thành viên theo lô, lọc danh sách thành viên... |
| `keepperId`           |    `UUID`    | ID của thành viên đang giữ quỹ nhóm (Thủ quỹ).                                                                                                                                                      | Người chịu trách nhiệm đối soát quỹ, giữ số dư thực tế...      |

---

## 2.1 Người gọi hàm (operatorId) <a id="21-nguoi-goi-ham-operatorid"></a>

- **Quy tắc vị trí:** Luôn đặt `UUID operatorId` ở **vị trí tham số đầu tiên** của phương thức trong Service/ServiceImpl
  nếu hàm đó yêu cầu định danh người thực thi.
- **Ý nghĩa:**
    - Đại diện cho `currentUserId` từ `SecurityContextUtil.currentUserId()`.
    - Được dùng làm đầu vào cho `GroupPermissionValidator` để kiểm tra quyền hạn (`verifyOwnerInGroupActive`,
      `verifyActiveMember`, v.v.).
    - Gán vào các trường kiểm toán như `createdBy`, `reviewedBy`.

---

## 2.2 Người tham gia (userId / memberId) <a id="22-nguoi-tham-gia-userid-memberid"></a>

- **Khi nào dùng `memberId`?**
    - Dùng trong các nghiệp vụ liên quan mật thiết tới thành viên trong nhóm:
        - Thêm thành viên: `addMember(UUID memberId, ...)`
        - Duyệt thành viên: `approve(UUID operatorId, UUID groupId, UUID memberId)`
        - Xóa thành viên: `removeMember(UUID operatorId, UUID groupId, UUID memberId)`
        - Chuyển giao chủ nhóm: `transferOwnership(UUID operatorId, UUID groupId, UUID memberId)`
- **Khi nào dùng `userId` / `transactorId`?**
    - Dùng khi biến đại diện cho người dùng nói chung (User ID từ auth/user module) hoặc trong bảng phân bổ chi
      tiêu/giao dịch:
        - Người thực hiện / đối ứng trong giao dịch (trả, góp, nhận, kiểm kê): `transactorId` hoặc
          `txn.getTransactorId()`
        - Người nhận hoàn tiền (trong DTO payout): `toUserId`
        - Người tham gia phân bổ (`TransactionParticipant`): `participant.getUserId()`

---

## 2.3 Người giữ quỹ (keepperId) <a id="23-nguoi-giu-quy-keepperid"></a>

- Quỹ nhóm gắn liền với một người giữ tiền thực tế (Thủ quỹ).
- Tên biến luôn là `keepperId` để phân biệt rõ với `operatorId` (người đang gọi API kiểm kê/cập nhật) và `ownerId`
  (chủ nhóm).

---

# 3. Chuẩn hóa Naming tại các Service <a id="3-chuan-hoa-naming-tai-cac-service"></a>

### 3.1 GroupService <a id="31-groupservice"></a>

```java
public interface GroupService {
    // Tra cứu
    GroupDetailRes findNotDeletedById(UUID groupId);

    // Người gọi tạo nhóm -> operatorId
    GroupDetailRes create(UUID operatorId, GroupCreateReq req);

    // Người gọi xem chi tiết -> operatorId
    GroupDetailRes detail(UUID operatorId, UUID groupId);

    // Người gọi cập nhật cấu hình -> operatorId (phải là Owner)
    GroupDetailRes update(UUID operatorId, UUID groupId, GroupUpdateReq req);

    // Người gọi lưu trữ / mở lưu trữ -> operatorId (phải là Owner)
    void archive(UUID operatorId, UUID groupId);
    void unarchive(UUID operatorId, UUID groupId);

    // operatorId: Chủ nhóm hiện tại; memberId: Thành viên nhận quyền
    void transferOwnership(UUID operatorId, UUID groupId, UUID memberId);

    // Người gọi xóa nhóm -> operatorId (phải là Owner)
    void delete(UUID operatorId, UUID groupId);

    // Người gọi gửi yêu cầu tham gia -> operatorId
    void join(UUID operatorId, GroupJoinReq req);
}
```

---

### 3.2 GroupMemberService <a id="32-groupmemberservice"></a>

```java
public interface GroupMemberService {
    // Thêm owner khởi tạo (memberId: người nhận vai trò Owner)
    MemberRes addOwner(UUID groupId, UUID memberId, Instant now);

    // Thêm 1 thành viên (memberId: người được thêm)
    MemberRes addMember(UUID memberId, UUID groupId, MemberStatus memberStatus, Instant now);

    // operatorId: Người thực hiện; memberIds: Danh sách người được thêm
    List<MemberRes> addMembers(UUID operatorId, UUID groupId, List<UUID> memberIds, Instant now);

    // Tra cứu thành viên theo memberId
    MemberRes findMember(UUID groupId, UUID memberId);
    MemberRes findMember(UUID groupId, UUID memberId, MemberStatus status);
    List<GroupMember> findMembers(UUID groupId, List<UUID> memberIds);
    List<MemberRes> findMembers(UUID groupId);

    // Người gọi tự rời nhóm -> operatorId
    void leave(UUID operatorId, UUID groupId);

    // operatorId: Người duyệt (Owner); memberId: Người được duyệt
    void approve(UUID operatorId, UUID groupId, UUID memberId);
    int approveAll(UUID operatorId, UUID groupId);

    // operatorId: Người xóa (Owner); memberId: Người bị xóa
    void removeMember(UUID operatorId, UUID groupId, UUID memberId);
}
```

---

### 3.3 FundService <a id="33-fundservice"></a>

```java
public interface FundService {
    // keepperId: Thành viên được gán giữ quỹ ban đầu
    GroupFundRes addFund(UUID groupId, UUID keepperId, Instant createdAt);

    // operatorId: Người gọi xem quỹ
    GroupFundRes getFund(UUID operatorId, UUID groupId);
    GroupFundRes getFund(UUID groupId);

    // operatorId: Người thực hiện sửa quỹ (phải là Owner)
    GroupFundRes updateFund(UUID operatorId, UUID groupId, GroupFundUpdateReq req);

    // operatorId: Người thực hiện kiểm kê (Owner hoặc Thủ quỹ)
    GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, GroupFundReconcileReq req);

    void adjustBalance(UUID fundId, Long delta);
}
```

---

### 3.4 GTransactionService & GTransactionReviewService <a id="34-grouptransactionservice"></a>

```java
public interface GTransactionService {
    // operatorId: Người tạo giao dịch (Member/Owner)
    GroupTransactionDetailRes create(UUID operatorId, UUID groupId, GroupTransactionCreateReq req);

    // operatorId: Người xem danh sách / chi tiết
    GroupTransactionListRes list(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter);
    GroupTransactionDetailRes detail(UUID operatorId, UUID groupId, UUID transactionId);

    // operatorId: Người sửa / xóa
    GroupTransactionDetailRes update(UUID operatorId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req);
    void delete(UUID operatorId, UUID groupId, UUID transactionId);

    // Đếm giao dịch chờ duyệt
    long countPendingForGroup(UUID groupId);
}

public interface GTransactionReviewService {
    // operatorId: Người duyệt / từ chối
    GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId);
    GroupTransactionDetailRes reject(UUID operatorId, UUID groupId, UUID transactionId);
    int bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);
    int bulkReject(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);
}
```

---

### 3.5 ReportService <a id="35-reportservice"></a>

```java
public interface ReportService {
    // operatorId: Thành viên yêu cầu xem báo cáo
    GroupSummaryReportRes getSummary(UUID operatorId, UUID groupId, String month);
    GroupBalanceReportRes getBalances(UUID operatorId, UUID groupId);
}
```

---

### 3.6 BalanceCalculator (Helper) <a id="36-balancecalculator"></a>

```java
public final class BalanceCalculator {
    public static MemberBalances calculateBalances(
            List<GTransaction> allTxns,
            List<Member> allMembers,
            UUID excludeTxnId
    );
}
```

---

### 3.7 GroupPermissionValidator <a id="37-grouppermissionvalidator"></a>

```java
@Component
public class GroupPermissionValidator {
    // operatorId: Kiểm tra người gửi request có active trong nhóm active không
    public MemberAuthInfo verifyActiveMemberInGroupActive(UUID groupId, UUID operatorId);

    // operatorId: Kiểm tra người gửi request có phải Owner không
    public MemberAuthInfo verifyOwnerInGroupActive(UUID groupId, UUID operatorId);

    // operatorId: Người thao tác; keepperId: Thủ quỹ giữ quỹ
    public void verifyOwnerOrTreasurer(UUID groupId, UUID operatorId, UUID keepperId);

    // Xác thực quyền chỉnh sửa giao dịch (Owner, Treasurer hoặc Creator)
    public void verifyTransactionEditPermission(GTransaction txn, UUID operatorId, MemberAuthInfo authInfo);
}
```

---

# 4. Quy ước Naming tại Controller & DTO <a id="4-quy-uoc-naming-tai-controller--dto"></a>

1. **Tại Controller:**
   ```java
   @PostMapping("/{id}/transfer-ownership")
   public ApiResponse<Void> transferOwnership(
           @PathVariable UUID id,
           @Valid @RequestBody GroupTransferOwnershipReq req) {
       UUID operatorId = SecurityContextUtil.currentUserId(); // Rõ ràng là operator
       groupService.transferOwnership(operatorId, id, req.newOwnerId());
       return ApiResponse.of(null);
   }
   ```
2. **Tại Request DTO:**
    - Trường đại diện người nhận: `memberId`, `newOwnerId`, `toUserId`.
    - Trường đại diện người trả: `fromUserId`, `payerUserId`.
    - Danh sách người tham gia: `participants`, `memberIds`, `excludedUserIds`.

---

# 5. Bảng tra cứu nhanh Anti-patterns <a id="5-bang-tra-cuu-nhanh-anti-patterns"></a>

| ❌ Không nên viết (Anti-pattern)                                    | ✅ Nên viết (Chuẩn hóa)                                           | Lý do                                                                             |
|:--------------------------------------------------------------------|:------------------------------------------------------------------|:----------------------------------------------------------------------------------|
| `transferOwnership(UUID userId, UUID groupId, UUID newOwnerUserId)` | `transferOwnership(UUID operatorId, UUID groupId, UUID memberId)` | Tránh nhầm lẫn giữa 2 ID người dùng (`userId` vs `newOwnerUserId`).               |
| `approve(UUID userId, UUID groupId, UUID memberId)`                 | `approve(UUID operatorId, UUID groupId, UUID memberId)`           | Phân biệt rõ `operatorId` (người duyệt) và `memberId` (người được duyệt).         |
| `removeMember(UUID userId, UUID groupId, UUID memberUserId)`        | `removeMember(UUID operatorId, UUID groupId, UUID memberId)`      | Ngắn gọn, chuẩn hóa `operatorId` và `memberId`.                                   |
| `getFund(UUID userId, UUID groupId)`                                | `getFund(UUID operatorId, UUID groupId)`                          | Thống nhất `operatorId` cho toàn bộ các method đọc/ghi dữ liệu nhóm.              |
| `findMembers(UUID groupId, List<UUID> uuids)`                       | `findMembers(UUID groupId, List<UUID> memberIds)`                 | Rõ ngữ nghĩa dữ liệu, không dùng tên chung chung `uuids`.                         |
| `leave(UUID groupId, UUID memberId)`                                | `leave(UUID operatorId, UUID groupId)`                            | `operatorId` là người gọi hàm tự rời nhóm, đặt ở đầu đồng bộ với các method khác. |
