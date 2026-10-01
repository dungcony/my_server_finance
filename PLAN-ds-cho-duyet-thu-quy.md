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

## 4. Câu hỏi cần bạn quyết

1. **"Danh sách chờ duyệt" là gì?** (a) thành viên xin vào nhóm, (b) giao dịch `PENDING`, hay (c) cả hai? Plan trên mới
   làm (a); (b) đã lọc được bằng `?status=PENDING`, nếu cần thêm thì tôi bổ sung endpoint đếm/badge.
2. **"Chưa có đổi thủ quỹ"** — ý bạn là *API chưa có* (thực tế đã có `PUT /fund-kepper`, có thể bạn chưa thấy vì tên
   viết sai chính tả `kepper`), hay *chưa dùng được* (thiếu kiểm tra, thiếu hiển thị)? Muốn tôi **đổi tên** URL thành
   `/fund-keeper` / `PATCH /fund` theo doc không? (đổi URL là đổi API contract nên cần bạn đồng ý).
3. `MemberRes` có cần thêm **tên + email** của thành viên không? Hiện chỉ có `userId`; làm vậy phải gọi sang
   `UserService` (đúng quy tắc 2.2, không import repository của `user`) — tốn thêm một lượt truy vấn.
4. Sau khi làm xong, bạn có muốn tôi chạy test nhóm liên quan (`MemberBehavier*`, `FundServiceImpl*`) không?

---
**Bạn comment ở đây / bấm process:**

