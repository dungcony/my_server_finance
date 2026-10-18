# Workflow — Nhóm và thành viên (group, member)

Code chính: [GroupServiceImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java) (nhóm),
[MemberBehavierServiceImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/MemberBehavierServiceImpl.java) (thành viên),
[FundServiceImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java) (thủ quỹ),
[MemberAuthCache](../../src/main/java/com/datn/financeapp/module/group/helper/MemberAuthCache.java) (cache quyền).

## Vai trò trong nhóm

| Vai trò | Là ai | Ghi ở đâu |
|---|---|---|
| Chủ nhóm (`OWNER`) | Người tạo nhóm, hoặc người được chuyển quyền | `group_members.role` |
| Thủ quỹ | Người giữ quỹ, mặc định là người tạo nhóm. Có thể là chủ nhóm hoặc một thành viên | `group_funds.keepper_id` |
| Thành viên (`MEMBER`) | Còn lại | `group_members.role` |

Trạng thái thành viên: `PENDING` (chờ duyệt) → `ACTIVE` → `LEFT` (tự rời) hoặc `REMOVED` (bị xoá).
Trạng thái nhóm: `ACTIVE` → `ARCHIVED` (lưu trữ, chỉ xem) → `DELETED`.

**Kiểm quyền:** mọi API đọc quyền qua `MemberAuthCache` (Redis, 30 phút). Chỉ thành viên **`ACTIVE`** mới có quyền; người `PENDING`, `LEFT`, `REMOVED` bị coi như người ngoài (`FORBIDDEN`).
Khi đổi chủ nhóm, thủ quỹ, trạng thái nhóm hoặc thành viên rời nhóm, code xoá cache của người liên quan hoặc tăng "version" của nhóm để cache cũ không còn dùng.

## Tạo nhóm — `POST /groups` (`@Idempotent`)

```
create
-> danh sách members không được rỗng, không được chứa chính người tạo
-> insert groups (status = ACTIVE, mã mời 8 ký tự ngẫu nhiên)
-> tạo quỹ group_funds (số dư 0, thủ quỹ = người tạo)
-> insert group_members: người tạo là OWNER, ACTIVE
-> insert group_members: từng người trong members là MEMBER, ACTIVE (không cần duyệt)
-> trả chi tiết nhóm kèm danh sách thành viên
```

⚠️ Hiện **tạo nhóm luôn lỗi**: gọi `fundService.create` truyền ngược 2 tham số (lỗi G1).

## Xem nhóm

| API | Ai gọi được | Trả về |
|---|---|---|
| `GET /groups` | Ai cũng gọi được | Các nhóm chưa xoá mà mình đang `ACTIVE`: vai trò của mình, số thành viên, số dư quỹ |
| `GET /groups/{id}` | Thành viên `ACTIVE` (nhóm lưu trữ vẫn xem được) | Chi tiết nhóm, quỹ, danh sách thành viên `ACTIVE` |

## Sửa, lưu trữ, xoá nhóm

| API | Ai làm được | Điều kiện, kết quả |
|---|---|---|
| `PATCH /groups/{id}` | Chủ nhóm | Đổi tên, mô tả, mục tiêu quỹ, bật/tắt quyết toán, bật/tắt "vào nhóm không cần duyệt" |
| `POST /groups/{id}/archive` | Thủ quỹ | Nhóm đang `ACTIVE`, không còn giao dịch `PENDING` → `ARCHIVED` |
| `POST /groups/{id}/unarchive` | Thủ quỹ | Nhóm đang `ARCHIVED` → `ACTIVE` |
| `DELETE /groups/{id}` | Thủ quỹ | Số dư quỹ = 0 và không còn giao dịch `PENDING` → `DELETED` |

## Vào nhóm bằng mã mời — `POST /groups/join?code=...` (`@Idempotent`)

```
join
-> tìm nhóm theo mã mời, nhóm chưa xoá (không → GROUP_NOT_FOUND)
-> nhóm đang lưu trữ → GROUP_ARCHIVED
-> đã ACTIVE → GROUP_MEMBER_ALREADY_EXISTS, đang PENDING → GROUP_MEMBER_PENDING
-> nhóm bật "vào không cần duyệt" → insert MEMBER, ACTIVE
   nhóm tắt                       → insert MEMBER, PENDING (chờ chủ nhóm duyệt)
```

Người từng rời nhóm (`LEFT`/`REMOVED`) vào lại thì tạo dòng `group_members` mới.

## Duyệt người xin vào (chủ nhóm, nhóm đang `ACTIVE`)

| API | Làm gì |
|---|---|
| `POST /groups/{groupId}/members/{memberUserId}/approve` | `PENDING` → `ACTIVE`, ghi `joined_at` |
| `POST /groups/{groupId}/approves` | Duyệt tất cả người đang `PENDING` |
| `POST /groups/{groupId}/members/{memberUserId}/reject` | Xoá hẳn dòng `PENDING` của người đó |
| `POST /groups/{groupId}/rejects` | Từ chối tất cả người đang `PENDING` |

## Xem và thêm thành viên

- `GET /groups/{groupId}/members?status=...`: chủ nhóm mặc định thấy cả `ACTIVE` lẫn `PENDING`; thành viên thường chỉ thấy `ACTIVE`; lọc `PENDING` thì chỉ chủ nhóm được.
- `POST /groups/{groupId}/members` (body danh sách id): thêm thẳng thành viên `ACTIVE`; id trùng nhau trong body → lỗi; người đã `ACTIVE`/`PENDING` → lỗi.

## Rời nhóm, xoá thành viên

```
POST /groups/{groupId}/leave  (thành viên tự rời)
-> phải đang ACTIVE (không → GROUP_MEMBER_REQUIRED)
-> chủ nhóm không được rời, phải chuyển quyền trước (GROUP_OWNER_TRANSFER_REQUIRED)
-> bắn MemberLeaveEvent
   -> nếu người rời là thủ quỹ → thủ quỹ chuyển về chủ nhóm
-> status = LEFT, ghi left_at, xoá cache quyền của người rời

DELETE /groups/{groupId}/members/{memberUserId}  (chủ nhóm xoá người khác)
-> không tự xoá chính mình; người bị xoá phải đang ACTIVE
-> bắn MemberLeaveEvent (như trên, quỹ về chủ nhóm nếu người bị xoá là thủ quỹ)
-> status = REMOVED, ghi left_at, xoá cache quyền của người bị xoá
```

Số dư của người đã rời vẫn giữ trong `group_member_balances`; báo cáo cân đối vẫn hiện họ nếu số dư khác 0 (xem [group_txn.md](group_txn.md)).

## Chuyển chủ nhóm, đổi thủ quỹ

| API | Ai làm được | Làm gì |
|---|---|---|
| `PUT /groups/{groupId}/owner-role/{memberUserId}/` | Chủ nhóm | Đổi vai trò 2 người cùng lúc: mình thành `MEMBER`, người kia thành `OWNER` (người kia phải `ACTIVE`, không được là chính mình) |
| `PUT /groups/{groupId}/fund-kepper` | Chủ nhóm | Đổi thủ quỹ, người mới phải là thành viên `ACTIVE`; xoá cache quyền cả nhóm |

## Lỗi đang mở (xem [review-05](../problem/review-05-group.md))

- G1: tạo nhóm luôn lỗi (truyền ngược tham số khi tạo quỹ).
- G9: `POST /members` chỉ kiểm nhóm chưa xoá, chưa kiểm người gọi là chủ nhóm, và thêm được cả khi nhóm đang lưu trữ.
- G10: thủ quỹ rời/bị xoá → quỹ về chủ nhóm, nhưng cache quyền của chủ nhóm vẫn ghi thủ quỹ cũ tối đa 30 phút.
- G11: vào nhóm bằng mã mời bị trùng thì lỗi 500 thay vì báo đã ở trong nhóm; `target = 0` qua được DTO nhưng DB chặn.
