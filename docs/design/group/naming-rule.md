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

    // Người gọi xem danh sách nhóm của mình -> operatorId
    List<GroupSummaryRes> list(UUID operatorId);

    // Người gọi xem chi tiết -> operatorId
    GroupDetailRes detail(UUID operatorId, UUID groupId);

    // Người gọi cập nhật cấu hình -> operatorId (phải là Owner)
    GroupDetailRes update(UUID operatorId, UUID groupId, GroupUpdateReq req);

    // Người gọi lưu trữ / mở lưu trữ -> operatorId (phải là Owner)
    void archive(UUID operatorId, UUID groupId);
    void unarchive(UUID operatorId, UUID groupId);

    // Người gọi xóa nhóm -> operatorId (phải là Owner)
    void delete(UUID operatorId, UUID groupId);

    // Người gọi gửi yêu cầu tham gia bằng mã mời -> operatorId
    void joinByCode(UUID operatorId, GroupJoinReq req);

    // Đếm việc chờ duyệt (badge) -> operatorId
    GroupPendingCountRes pendingCount(UUID operatorId, UUID groupId);
}
```

---

### 3.2 MemberBehavierService & MemberService <a id="32-memberservice"></a>

```java
public interface MemberBehavierService {
    // operatorId: Thành viên xem danh sách; status: Bộ lọc trạng thái (tuỳ chọn)
    List<MemberRes> listMembers(UUID operatorId, UUID groupId, MemberStatus status);

    // operatorId: Chủ nhóm thêm trực tiếp thành viên; req: danh sách memberIds
    List<MemberRes> ownerAddMembers(UUID operatorId, UUID groupId, MemberAddReq req);

    // operatorId: Thành viên tự rời nhóm
    void leave(UUID operatorId, UUID groupId);

    // operatorId: Chủ nhóm duyệt / từ chối thành viên PENDING
    void approve(UUID operatorId, UUID groupId, UUID memberId);
    int approveAll(UUID operatorId, UUID groupId);
    void reject(UUID operatorId, UUID groupId, UUID memberId);
    int rejectAll(UUID operatorId, UUID groupId);

    // operatorId: Chủ nhóm mời thành viên ra khỏi nhóm
    void removeMember(UUID operatorId, UUID groupId, UUID memberId);

    // operatorId: Chủ nhóm chuyển quyền; memberId: Người nhận quyền Owner
    void transferOwnership(UUID operatorId, UUID groupId, UUID memberId);
}

public interface MemberService {
    // Thao tác bản ghi nội bộ
    Optional<MemberRes> create(MemberCreateReq req);
    List<MemberRes> creates(List<MemberCreateReq> req);

    long countActiveMembers(UUID groupId);
    long countPendingMembers(UUID groupId);

    MemberRes getMember(UUID groupId, UUID memberId, MemberStatus status);
    List<MemberRes> getActivateMembers(UUID groupId);
    List<MemberRes> getMembersWithStatusIn(UUID groupId, List<MemberStatus> statuses);
    List<UUID> findIdAllMember(UUID groupId);
    boolean allMemberInGroup(UUID groupId, List<UUID> memberIds);
    void assertNotInGroup(UUID groupId, UUID memberId);
}
```

---

### 3.3 FundService <a id="33-fundservice"></a>

```java
public interface FundService {
    // operatorId: Chủ nhóm tạo nhóm và gán giữ quỹ ban đầu
    FundRes create(UUID groupId, UUID operatorId, Instant now);

    // operatorId: Chủ nhóm bàn giao thủ quỹ
    FundRes updateFundKeepper(UUID operatorId, UUID groupId, FundKepperUpdateReq req);

    // operatorId: Người thực hiện kiểm kê (Owner hoặc Thủ quỹ)
    GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, FundReconcileReq req);

    // Điều chỉnh số dư trực tiếp khi giao dịch hoàn tất / hoàn tác
    void adjustBalance(UUID fundId, Long delta);
}
```

---

### 3.4 GTransactionService & GTransactionReviewService <a id="34-grouptransactionservice"></a>

```java
public interface GTransactionService {
    // operatorId: Người tạo giao dịch (Member/Owner)
    GroupTransactionDetailRes create(UUID operatorId, UUID groupId, GroupTransactionCreateReq req);

    // operatorId: Người xem danh sách giao dịch chung
    GroupTransactionListRes list(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter);

    // operatorId: Người xem danh sách giao dịch do chính mình ghi nhận
    GroupTransactionListRes myList(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter);

    // operatorId: Người duyệt xem danh sách giao dịch chờ duyệt (Owner/Treasurer)
    GroupTransactionListRes listPending(UUID operatorId, UUID groupId, Integer page, Integer size);

    // operatorId: Người xem chi tiết giao dịch
    GroupTransactionDetailRes detail(UUID operatorId, UUID groupId, UUID transactionId);

    // operatorId: Người sửa / xóa (Creator hoặc Owner)
    GroupTransactionDetailRes update(UUID operatorId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req);
    void delete(UUID operatorId, UUID groupId, UUID transactionId);

    // Đếm giao dịch chờ duyệt
    long countPendingForGroup(UUID groupId);
}

public interface GTransactionReviewService {
    // operatorId: Người duyệt / từ chối (Owner hoặc Treasurer)
    GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId);
    GroupTransactionDetailRes reject(UUID operatorId, UUID groupId, UUID transactionId);
    GroupTransactionBulkReviewRes bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);
    GroupTransactionBulkReviewRes bulkReject(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);
}
```

---

### 3.5 GReportService <a id="35-reportservice"></a>

```java
public interface GReportService {
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
   @PutMapping("/owner-role/{memberUserId}/")
   public ApiResponse<Void> updateMemberRole(
           @PathVariable UUID groupId,
           @PathVariable UUID memberUserId) {
       UUID operatorId = SecurityContextUtil.currentUserId(); // Rõ ràng là operator
       memberBehavierService.transferOwnership(operatorId, groupId, memberUserId);
       return ApiResponse.of(null);
   }
   ```
2. **Tại Request DTO:**
   - Trường đại diện người nhận: `memberId`, `memberUserId`, `toUserId`.
   - Trường đại diện người thực hiện đối ứng / người chi: `transactorId`, `userId`.
   - Danh sách người tham gia: `participants`, `memberIds`, `excludedUserIds`.

---

# 5. Bảng tra cứu nhanh Anti-patterns <a id="5-bang-tra-cuu-nhanh-anti-patterns"></a>

| ❌ Không nên viết (Anti-pattern)                                    | ✅ Nên viết (Chuẩn hóa)                                           | Lý do                                                                             |
|:--------------------------------------------------------------------|:------------------------------------------------------------------|:----------------------------------------------------------------------------------|
| `transferOwnership(UUID userId, UUID groupId, UUID newOwnerUserId)` | `transferOwnership(UUID operatorId, UUID groupId, UUID memberId)` | Tránh nhầm lẫn giữa 2 ID người dùng (`userId` vs `newOwnerUserId`).               |
| `approve(UUID userId, UUID groupId, UUID memberId)`                 | `approve(UUID operatorId, UUID groupId, UUID memberId)`           | Phân biệt rõ `operatorId` (người duyệt) và `memberId` (người được duyệt).         |
| `removeMember(UUID userId, UUID groupId, UUID memberUserId)`        | `removeMember(UUID operatorId, UUID groupId, UUID memberId)`      | Ngắn gọn, chuẩn hóa `operatorId` và `memberId`.                                   |
| `updateFund(UUID userId, UUID groupId, FundKepperUpdateReq req)`    | `updateFundKeepper(UUID operatorId, UUID groupId, req)`           | Thống nhất `operatorId` cho toàn bộ các method đọc/ghi dữ liệu nhóm.              |
| `findMembers(UUID groupId, List<UUID> uuids)`                       | `findMembers(UUID groupId, List<UUID> memberIds)`                 | Rõ ngữ nghĩa dữ liệu, không dùng tên chung chung `uuids`.                         |
| `leave(UUID groupId, UUID memberId)`                                | `leave(UUID operatorId, UUID groupId)`                            | `operatorId` là người gọi hàm tự rời nhóm, đặt ở đầu đồng bộ với các method khác. |
