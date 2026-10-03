# Plan: Cho phép **đọc** và **xoá** nhóm đang `ARCHIVED` (+ sửa `left_at` bị mất)

- **Phạm vi:** `GroupPermissionValidator` và các hàm đọc của `GroupServiceImpl`, `GTransactionServiceImpl`, `GReportServiceImpl`, `MemberBehavierServiceImpl`; `GroupServiceImpl.delete`/`unarchive`; `MemberRes.withDisplay`
- **Trạng thái:** ✅ ĐÃ SỬA (04/10/2026) — main biên dịch sạch, test biên dịch được; **chưa chạy test**. Bổ sung theo yêu cầu: sửa luôn `MemberBehavierServiceImplTest` (đổi `ProfileService` → `UserService`, stub `getAuthInfo(..., true)`, thêm 1 test cờ)
- **Xác nhận:** `[x] PROCESS`
- **API contract:** giữ nguyên (sửa cho đúng `api.md` mục "Lưu trữ và mở lại nhóm", `rule.md` quy tắc 26, và `left_at` ở `api.md:345`)
- **Đã chốt hết:** thêm tham số `boolean allowArchived`; **mặc định (bản 2 tham số) vẫn chỉ cho nhóm đang hoạt động**, hàm đọc mới truyền `true`; bỏ `verifyOwnerAllowArchived`; gộp lỗi `left_at`

---

## 0. Hiện trạng

Quy tắc trong tài liệu: nhóm `ARCHIVED` **"mọi endpoint `GET` vẫn dùng được"**, chỉ chặn thao tác ghi; "vẫn làm được: `unarchive` và `DELETE`" (`api.md:465-469`).

Code đang làm khác: `getAuthInfo` → `validateAuthInfo` ném `GROUP_ARCHIVED` khi nhóm `ARCHIVED` (`GroupPermissionValidator.java:199`), và **cả hàm đọc lẫn hàm ghi đều đi qua nó**. Các hàm sai:

| Class | Hàm | Kiểu | Hiện tại | Từ đâu |
|---|---|---|---|---|
| `GroupServiceImpl` | `detail` | đọc | ném `GROUP_ARCHIVED` | **mới** (đổi sang `verifyMember`) |
| `GroupServiceImpl` | `pendingCount` | đọc | ném `GROUP_ARCHIVED` | cũ |
| `GTransactionServiceImpl` | `detail`, `list`, `myList`, `listPending` | đọc | ném `GROUP_ARCHIVED` | cũ |
| `GReportServiceImpl` | `getSummary`, `getBalances` | đọc | ném `GROUP_ARCHIVED` | cũ |
| `MemberBehavierServiceImpl` | `listMembers` | đọc | ném `GROUP_ARCHIVED` | cũ |
| `GroupServiceImpl` | `delete` | ghi nhưng được phép | ném `GROUP_ARCHIVED` | cũ |

Hàm ghi còn lại (tạo/sửa/xoá/duyệt giao dịch, kiểm kê, sửa quỹ, sửa nhóm, `archive`, duyệt/mời rời/chuyển quyền) **phải tiếp tục bị chặn** và **không phải sửa gì**.

Lỗi thứ hai: `MemberRes.withDisplay` truyền `null` vào `leftAt` (`MemberRes.java:25`), nên `GET /groups/{id}/members?status=LEFT|REMOVED` (qua `MemberViewEnricher.enrich`) luôn trả `left_at: null`. Tác động hiện tại nhỏ vì danh sách mặc định chỉ có `ACTIVE`/`PENDING`.

---

## 1. Các file sẽ thay đổi

**Main (6 file)**

| # | File | Thay đổi |
|---|---|---|
| 1 | `group/validator/GroupPermissionValidator.java` | Thêm bản 3 tham số `getAuthInfo(..., allowArchived)` và `verifyOwner(..., allowArchived)`; bản 2 tham số cũ gọi sang với `false`; `verifyMember` đổi thành 3 tham số; **xoá** `verifyOwnerAllowArchived` |
| 2 | `group/service/impl/GroupServiceImpl.java` | `detail`, `pendingCount`, `delete`, `unarchive` truyền `true` |
| 3 | `group/service/impl/GTransactionServiceImpl.java` | `detail`, `list`, `myList`, `listPending` truyền `true`; `requireReviewer` thêm bản 3 tham số |
| 4 | `group/service/impl/GReportServiceImpl.java` | `getSummary`, `getBalances` truyền `true` |
| 5 | `group/service/impl/MemberBehavierServiceImpl.java` | `listMembers` truyền `true` |
| 6 | `group/dto/response/member/MemberRes.java` | `withDisplay` giữ nguyên `leftAt` thay vì truyền `null` |

**Test (đi cùng commit, không giảm số test)**

| # | File | Thay đổi |
|---|---|---|
| 7 | `group/validator/GroupPermissionValidatorTest.java` **(mới)** | Test cho các hàm xác thực (hiện chưa có test nào cho class này) |
| 8 | `group/dto/response/member/MemberResTest.java` **(mới)** | 2 test: `withDisplay` giữ `leftAt`/`joinedAt`/`status`, chỉ thêm tên và cờ thủ quỹ |
| 9 | `group/service/GroupReportServiceTest.java` | Thêm 2 test: báo cáo trên nhóm `ARCHIVED` vẫn trả dữ liệu (validator ở test này là bản thật) |
| 10 | `group/service/impl/GTransactionServiceImplTest.java` | Thêm 3 stub lenient `getAuthInfo(groupId, user, true)` ở `setUp` (cho các test `listPending`); thêm test kiểm cờ: hàm đọc `true`, `confirm` dùng bản mặc định |
| 11 | `group/service/impl/GroupServiceImplTest.java` | Đổi 3 stub `getAuthInfo(groupId, userId)` của `pendingCount` sang `(…, true)`; thêm test `detail`, `pendingCount`, `delete` trên nhóm `ARCHIVED` |

**Không đổi:** `FundServiceImpl` và mọi hàm ghi (vẫn gọi bản mặc định chặn lưu trữ), `FundServiceImplTest`, controller, DTO, `api.md`/`rule.md` (tài liệu đã đúng, code mới lệch).

---

## 2. Lý do thay đổi

- Code lệch tài liệu: mở nhóm đã lưu trữ từ danh sách rồi bấm vào thì `GET /groups/{id}` báo `409 GROUP_ARCHIVED`; không xem được giao dịch, báo cáo, thành viên; chủ nhóm không xoá được nhóm lưu trữ dù tài liệu cho phép.
- **Vì sao tham số boolean với mặc định chặn:** không thêm hàm mới tên mới, bỏ được `verifyOwnerAllowArchived` (nó chính là `verifyOwner(..., true)`), và vì mặc định là *chặn* nên hàm ghi không phải sửa — chỉ hàm đọc chủ động truyền `true`. Nếu mặc định là cho qua thì hàm ghi nào quên truyền `false` sẽ ghi được vào nhóm lưu trữ. Tên tham số `allowArchived`.
- Đổi lại: hàm đọc **mới viết sau này** mà quên truyền `true` thì sẽ bị chặn nhóm lưu trữ (lỗi lộ ra ngay khi thử, không nguy hiểm). Test ở #7 và #9 giữ cho hai chiều không bị đảo.
- Nhóm đã xoá (`DELETED`) vẫn báo `GROUP_NOT_FOUND` ở mọi trường hợp.
- **Khi nào cần `leftAt`:** nó được ghi lúc thành viên rời hoặc bị mời rời (`MemberBehavierServiceImpl:110` và `:193`, kèm `status = LEFT/REMOVED`); ràng buộc DB `ck_gm_dates` bắt `ACTIVE` phải `left_at IS NULL` và `LEFT/REMOVED` phải `NOT NULL`. Mỗi dòng `group_members` là một **khoảng thời gian ở trong nhóm** `[joinedAt, leftAt)`, rời rồi quay lại thì có dòng mới. Nó được **đọc** ở:
  - `BalanceCalculator` (qua `GroupMemberPeriod.isActiveAt`): giao dịch không chỉ định người chia thì chia đều cho **những người có mặt lúc giao dịch phát sinh**. Thiếu `leftAt`, người đã rời bị coi là còn ở nhóm mãi và bị chia cả các khoản chi sau khi họ rời — số tiền sai mà vẫn trông hợp lý;
  - `MemberRepository.findMemberUserIdsAtOccurredAt` (SQL `left_at IS NULL OR left_at > :occurredAt`) — cùng ý nghĩa;
  - API: trường `left_at` ở danh sách thành viên (`api.md:345`).

  Với thành viên đang `ACTIVE` thì `leftAt` luôn `null`, nên chỉ có nghĩa với `LEFT`/`REMOVED`. `withDisplay` chỉ thêm tên hiển thị và cờ thủ quỹ nên phải giữ nguyên mọi trường còn lại; hiện nó đang xoá `leftAt` của người đã rời trước khi trả ra API.

---

## 3. Hướng giải quyết / code xem trước

### 3.1 `GroupPermissionValidator`

```java
    // mặc định chặn nhóm ARCHIVED — dùng cho mọi thao tác ghi
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId) {
        return getAuthInfo(groupId, operatorId, false);
    }

    /**
     * Lấy thông tin auth và xác thực nhóm chưa bị xoá cùng tư cách thành viên (ACTIVE).
     *
     * @param allowArchived true cho thao tác chỉ đọc (và xoá/mở lại nhóm); false thì nhóm ARCHIVED bị chặn bằng GROUP_ARCHIVED
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId, boolean allowArchived) {
        if (groupId == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return validateAuthInfo(groupRepository.findAuthInfo(groupId, operatorId), allowArchived);
    }

    public void verifyOwner(UUID groupId, UUID operatorId) {
        verifyOwner(groupId, operatorId, false);
    }

    public void verifyOwner(UUID groupId, UUID operatorId, boolean allowArchived) {
        var info = getAuthInfo(groupId, operatorId, allowArchived);

        if (info.memberRole() != MemberRole.OWNER)
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
    }

    private MemberAuthInfo validateAuthInfo(Optional<MemberAuthInfo> authInfo, boolean allowArchived) {
        MemberAuthInfo info = requireNotDeleted(authInfo);

        if (!allowArchived && info.groupStatus() == GroupStatus.ARCHIVED)
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);

        return info;
    }
```

- `verifyMember(groupId, operatorId, allowArchived)`: chỉ còn bản 3 tham số (chỉ `detail` gọi, và gọi với `true`; giữ bản 2 tham số sẽ thành code chết). Giữ kiểm tra trạng thái thành viên.
- `getAuthInfo(String inviteCode, UUID)` và `verifyOwnerOrTreasurer` giữ nguyên (hàm ghi, dùng bản mặc định).

### 3.2 Đổi nơi gọi

| Hàm | Đổi |
|---|---|
| `GroupServiceImpl.detail` | `verifyMember(g, u)` → `verifyMember(g, u, true)` |
| `GroupServiceImpl.pendingCount` | `getAuthInfo(g, u)` → `getAuthInfo(g, u, true)` |
| `GroupServiceImpl.delete`, `unarchive` | `verifyOwner(g, u)` / `verifyOwnerAllowArchived(g, u)` → `verifyOwner(g, u, true)` |
| `GTransactionServiceImpl.detail`, `list`, `myList` | `getAuthInfo(g, u)` → `getAuthInfo(g, u, true)` |
| `GTransactionServiceImpl.listPending` | `requireReviewer(g, u)` → `requireReviewer(g, u, true)` |
| `GReportServiceImpl.getSummary`, `getBalances` | `getAuthInfo(g, u)` → `getAuthInfo(g, u, true)` |
| `MemberBehavierServiceImpl.listMembers` | `getAuthInfo(g, u)` → `getAuthInfo(g, u, true)` |

`requireReviewer` dùng chung cho `listPending` (đọc) và 4 hàm duyệt (ghi). Giữ bản 2 tham số (mặc định chặn) cho 4 hàm duyệt, thêm bản 3 tham số:

```java
    // xác thực người thực hiện đang trong nhóm và là chủ nhóm hoặc thủ quỹ; mặc định chặn nhóm lưu trữ
    private void requireReviewer(UUID groupId, UUID operatorId) {
        requireReviewer(groupId, operatorId, false);
    }

    private void requireReviewer(UUID groupId, UUID operatorId, boolean allowArchived) {
        MemberAuthInfo info = permissionValidator.getAuthInfo(groupId, operatorId, allowArchived);
        if (!info.isOwner() && !info.isTreasurer())
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
    }
```

### 3.3 `MemberRes.withDisplay`

```java
    public MemberRes withDisplay(String displayName, boolean isTreasurer) {
        return new MemberRes(id, userId, role, status, joinedAt, leftAt, displayName, isTreasurer);
    }
```

### 3.4 Test

- **#7 `GroupPermissionValidatorTest`** (Mockito, mock `GroupRepository`):
  - `getAuthInfo(g, u, true)` trả thông tin khi nhóm `ARCHIVED`; `getAuthInfo(g, u)` (mặc định) và `getAuthInfo(g, u, false)` ném `GROUP_ARCHIVED` — **khoá hàm ghi không bị mở nhầm**;
  - nhóm `DELETED` hoặc không có bản ghi → `GROUP_NOT_FOUND` ở cả hai cờ; tham số `null` → `VALIDATION_ERROR`;
  - `verifyOwner`: Owner trên nhóm `ARCHIVED` qua với `true`, bị chặn với bản mặc định; thành viên thường → `FORBIDDEN_OWNER_REQUIRED`.
- **#8 `MemberResTest`:** `withDisplay` giữ `leftAt` của thành viên đã rời (đỏ đúng lý do trước khi sửa) và vẫn `null` với thành viên đang ở nhóm.
- **#9:** stub `findAuthInfo` trả nhóm `ARCHIVED`, kiểm `getSummary`/`getBalances` trả dữ liệu.
- **#10, #11:** các test mock `GroupPermissionValidator` đổi stub của hàm đọc sang bản 3 tham số và thêm `verify` cờ đúng; hàm ghi vẫn stub bản 2 tham số như cũ.

---

## 4. Quyết định (đã chốt hết)

| # | Nội dung | Kết quả |
|---|---|---|
| Q1 ✅ | Cách sửa | Thêm tham số boolean vào hàm sẵn có, bỏ `verifyOwnerAllowArchived` |
| Q1b ✅ | Bắt buộc truyền hay có mặc định | **Có mặc định = chặn nhóm lưu trữ** (chỉ lấy nhóm `ACTIVE`); hàm đọc truyền `true` |
| Q1c ✅ | Tên tham số | `allowArchived` |
| Q3 ✅ | Lỗi `left_at` | Gộp vào plan này, sửa một dòng ở `withDisplay` |

---

## 5. Ngoài phạm vi / lưu ý — **không sửa nếu bạn chưa bảo**

- `MemberBehavierServiceImplTest` (module `group`) đang không biên dịch vì còn mock `ProfileService` đã xoá, và nó cũng có 2 stub `getAuthInfo(groupId, ownerId)` của `listMembers` sẽ phải đổi sang bản `true`. Chưa thêm được test cho `listMembers`; sửa test đó là việc riêng (đợt refactor user).
- `MemberViewEnricher` NPE khi user vắng trong `getNames`: **không cần sửa** — `group_members.user_id` có khoá ngoại `ON DELETE CASCADE` (V18) và code không có đường nào xoá cứng user.

---

## 6. Cách kiểm tra

- Biên dịch main ở project thật.
- Sau khi sửa tôi sẽ hỏi bạn có muốn chạy test không; nếu chạy thì trên bản sao scratchpad: `GroupPermissionValidatorTest`, `MemberResTest`, `GroupReportServiceTest`, `GTransactionServiceImplTest`, `GroupServiceImplTest` (project thật chưa chạy `mvn test` được vì test auth/user đang vỡ biên dịch).
- API không đổi nên không cần cập nhật `api/`.

---

## 7. Bổ sung (✅ ĐÃ SỬA): bỏ `verifyMember` khỏi `GroupServiceImpl.detail`

Từ nhận xét review: sau khi `buildGroupDetailRes(group, operatorId)` đã tự lấy thành viên `ACTIVE` và ném `FORBIDDEN_NOT_GROUP_MEMBER` nếu người gọi không có trong danh sách, thì `verifyMember` chỉ thêm một truy vấn `findAuthInfo` thừa. Đồng ý bỏ, vì còn có thêm hai lợi ích:

- `detail` đọc được nhóm `ARCHIVED` mà không cần cờ (`findNotDeletedWithFundById` chỉ loại nhóm `DELETED`), nên không cần `verifyMember(..., true)`.
- Mã lỗi đúng `rule.md` quy tắc 8: người ngoài nhóm có thật → `403 FORBIDDEN_NOT_GROUP_MEMBER`. Hiện `verifyMember` → `findAuthInfo` rỗng → `GROUP_NOT_FOUND` (404), lệch quy tắc. Nhóm không tồn tại vẫn `404`.

Đổi lại: người ngoài nhóm gọi vào nhóm có thật thì code nạp nhóm + quỹ + danh sách thành viên rồi mới từ chối (chỉ trên đường bị từ chối).

Thay đổi nếu duyệt: xoá `verifyMember` (chỉ `detail` gọi) khỏi `GroupPermissionValidator` và 2 test `verifyMember_*` của nó; `detail_archivedGroup_stillReadable` đổi `verify(...verifyMember...)` thành `verifyNoInteractions(permissionValidator)`; thêm test người ngoài nhóm → `FORBIDDEN_NOT_GROUP_MEMBER`. Cờ `allowArchived` của `getAuthInfo`/`verifyOwner` giữ nguyên.

---

**Hãy nhắn "process" để tôi bắt đầu code (viết test trước, rồi sửa code).**
