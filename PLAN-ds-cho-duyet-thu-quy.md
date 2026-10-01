# PLAN — Danh sách chờ duyệt + hiển thị thủ quỹ + đổi thủ quỹ

> Trạng thái: **CHỜ BẠN DUYỆT** — chưa sửa dòng code nào. Ghi comment ngay dưới từng mục, rồi bấm **process** để tôi bắt
> đầu.

## 0. Hiện trạng (đã đọc code)

| Việc                           | Hiện có                                                                                                                                                                                                  | Thiếu                                                                                                                                   |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Danh sách thành viên chờ duyệt | `approve` / `approveAll` / `reject` / `rejectAll` đều có ở `MemberController`. Chủ nhóm chỉ thấy người `PENDING` **lẫn trong** `GET /groups/{id}` → `members[]` (`GroupServiceImpl.buildGroupDetailRes`) | **Không có endpoint riêng** liệt kê người chờ duyệt. Doc `api.md` mục 3.1.5b còn quy định `GET /groups/{id}/members?status=` — chưa làm |
| Danh sách giao dịch chờ duyệt  | `GET /groups/{id}/transactions?status=PENDING` (`GroupTransactionController.list`) + confirm/reject/bulk                                                                                                 | Chưa có đường tắt, chưa có số đếm để hiện badge. *(Chỉ cần làm nếu ý bạn là giao dịch — xem câu hỏi 1)*                                 |
| Hiển thị thủ quỹ               | `GroupDetailRes.fund.keepperId` có trả về                                                                                                                                                                | `MemberRes` **không có cờ thủ quỹ, không có tên/email** → FE phải tự ghép UUID. Test page cũng không đánh dấu ai là thủ quỹ             |
| Đổi thủ quỹ                    | `PUT /groups/{id}/fund-kepper` (`FundController`) + ô nhập trên `group-test.html` **đã có** (chỉ owner)                                                                                                  | Chưa có: GET xem quỹ riêng; lỗi sai mã khi người mới không `ACTIVE`; chưa có test                                                       |

⇒ "Chưa có đổi thủ quỹ" thực ra **đã có API**, phần thiếu nằm ở chỗ hiển thị và kiểm tra (câu hỏi 2).

## 1. Các file sẽ thay đổi

**Phần A — danh sách chờ duyệt (thành viên)**

1. `group/controller/MemberController.java` — thêm `GET /groups/{groupId}/members?status=`.
2. `group/service/MemberBehavierService.java` + `impl/MemberBehavierServiceImpl.java` — thêm
   `listMembers(operatorId, groupId, status)`: chỉ `OWNER` mới được xem `PENDING`.
3. `group/repository/MemberRepository.java` — dùng lại `findAllByGroupIdAndStatus`, **không cần query mới**.

**Phần B — hiển thị thủ quỹ**

4. `group/dto/response/member/MemberRes.java` — thêm `isTreasurer` (Boolean).
5. `group/mapper/MemberMapper.java`, `GroupMapper.java` — map thêm trường mới (`unmappedTargetPolicy = ERROR` nên bắt
   buộc khai báo).
6. `group/service/impl/GroupServiceImpl.java` — trong `buildGroupDetailRes`, đánh dấu
   `isTreasurer = (userId == fund.keepperId)`.
7. `static/pages/group-test.html`, `static/js/group-test.js` — hiện nhãn "Thủ quỹ" cạnh thành viên + khối "Danh sách chờ
   duyệt" gọi API mới.

**Phần C — đổi thủ quỹ**

8. `group/service/impl/FundServiceImpl.java` — `updateFundKeepper`: người mới phải là `ACTIVE` (hiện `getAuthInfo` không
   bắt lỗi riêng, trả `GROUP_NOT_FOUND` gây khó hiểu) → ném `GROUP_MEMBER_NOT_FOUND`/`HOLDER_NOT_MEMBER` theo doc.
9. `group/controller/FundController.java` — thêm `GET /groups/{groupId}/fund` (mọi thành viên ACTIVE xem được quỹ + thủ
   quỹ).

**Test (đi cùng code, TDD đỏ → xanh)**

10. `MemberBehavierServiceImplTest` (mới hoặc thêm) — owner thấy PENDING, member thường bị `FORBIDDEN_OWNER_REQUIRED`
    khi lọc PENDING.
11. `FundServiceImplTest` (mới) — đổi thủ quỹ sang người `LEFT`/`PENDING` bị chặn; đổi thành công cập nhật `keepperId`.

**Tài liệu:** `docs/prd/03-NHOM-CHUNG-QUY/thiet-ke/api.md` — chỉnh mục 3.1.5b và 3.2 cho khớp thực tế (`isTreasurer`,
tên endpoint `/fund-kepper` hay `/fund`).

## 2. Lý do thay đổi

- Chủ nhóm cần một danh sách gọn để duyệt, không phải lục trong `members[]` của chi tiết nhóm.
- Người dùng phải biết **ai là thủ quỹ** ngay trên danh sách thành viên (quy tắc: thủ quỹ là người xác nhận giao dịch
  `PENDING`).
- Đổi thủ quỹ phải chặn người đã rời/chưa được duyệt, nếu không quỹ có thể rơi vào tay người không còn trong nhóm.

## 3. Hướng giải quyết / code xem trước

```java
// MemberController
@GetMapping("/members")
public ApiResponse<List<MemberRes>> listMembers(
        @PathVariable UUID groupId,
        @RequestParam(required = false) MemberStatus status) {
    return ApiResponse.of(memberBehavierService.listMembers(SecurityContextUtil.currentUserId(), groupId, status));
}

// MemberBehavierServiceImpl
@Transactional(readOnly = true)
public List<MemberRes> listMembers(UUID operatorId, UUID groupId, MemberStatus status) {
    MemberAuthInfo info = permissionValidator.getAuthInfo(groupId, operatorId);
    // chỉ chủ nhóm được xem người chờ duyệt, thành viên thường chỉ thấy ACTIVE
    if (status == MemberStatus.PENDING && info.memberRole() != MemberRole.OWNER)
        throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
    ...
}

// MemberRes
public record MemberRes(UUID id, UUID userId, MemberRole role, MemberStatus status,
                        Instant joinedAt, Boolean isTreasurer) {
}
```

Mặc định `status` bỏ trống → trả `ACTIVE + PENDING` (owner) / `ACTIVE` (member), giống `buildGroupDetailRes`.

## 4. Đã chốt (theo trả lời của bạn)

| # | Quyết định |
|---|---|
| 1 | Làm **cả hai**: danh sách thành viên chờ duyệt **và** danh sách giao dịch chờ duyệt |
| 2 | Hiển thị thủ quỹ ở danh sách member — cách làm ở mục 5 (không khó) |
| 3 | `MemberRes` thêm **tên hiển thị**, lấy bằng `ProfileService.getDisplayNames(...)` (đã có sẵn, 1 query cho cả danh sách) |
| 4 | Tôi **không chạy test**, bạn tự chạy |

Còn **chưa chốt**: có đổi tên URL `fund-kepper` → `fund-keeper` không? Plan này mặc định **giữ nguyên** (không đụng API contract).

## 5. Hiển thị thủ quỹ ở danh sách member — làm thế nào

Thủ quỹ không nằm trong bảng `group_members` mà nằm ở `group_funds.keepper_id`. Nên chỉ cần **so sánh `userId` của từng member với `keepperId`** lúc dựng response:

```java
// GroupServiceImpl.buildGroupDetailRes: đã có sẵn group.getFund() nên không tốn query
UUID keeperId = group.getFund() != null ? group.getFund().getKeepperId() : null;
Map<UUID, String> names = profileService.getDisplayNames(members.stream().map(MemberRes::userId).toList());
List<MemberRes> enriched = members.stream()
        .map(m -> m.withDisplay(names.get(m.userId()), m.userId().equals(keeperId)))
        .toList();
```

`MemberRes` thêm 2 trường: `displayName` (String), `isTreasurer` (boolean). Dùng cho **cả** `GET /groups/{id}` lẫn `GET /groups/{id}/members`. Riêng `GET /members` cần lấy `Fund` qua `FundService` (thêm `getKeeperId(groupId)`), vì không có sẵn `group`.

## 6. Bổ sung phần giao dịch chờ duyệt (câu 1b)

- `GroupTransactionController`: thêm `GET /groups/{groupId}/transactions/pending` — chỉ **thủ quỹ / OWNER** xem được (cùng quyền với confirm/reject), trả `GroupTransactionListRes` đã lọc `status = PENDING`, có phân trang `page`, `size`.
- Thêm `GET /groups/{groupId}/transactions/pending-count` → `{ "pending_transactions": n, "pending_members": m }` để FE hiện badge (member count chỉ trả cho OWNER, người khác trả 0).
- Tái sử dụng `gTransactionService.list` với filter `status = PENDING`, và `countPendingForGroup` đã có — không thêm query.

File thêm vào danh sách ở mục 1: `GroupTransactionController`, `GTransactionService`/`GTransactionServiceImpl` (hàm `listPending`, `countPending`), `FundService` (`getKeeperId`), `MemberRes` + hai mapper, `group-test.html/js` (hai khối danh sách + badge).

---
**Bạn comment ở đây / bấm process (viết chữ `process` bên dưới để tôi bắt đầu code):**

