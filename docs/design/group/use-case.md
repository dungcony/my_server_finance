# ĐẶC TẢ USE CASE (USE CASE SPECIFICATIONS) — PHÂN HỆ NHÓM CHUNG QUỸ

> **File này trả lời:** Các tác nhân trong hệ thống có thể thực hiện những chức năng gì, trong điều kiện nào, luồng
> tương tác chuẩn và luồng xử lý ngoại lệ ra sao.  
> Luật nghiệp vụ → [rule.md](rule.md) · Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Luồng xử lý & Tính
> toán → [pipeline.md](pipeline.md) · Endpoint & DTO → [api.md](api.md)

---

## 📑 MỤC LỤC

- [1. Danh mục Tác nhân (Actors)](#1-danh-muc-tac-nhan)
- [2. Sơ đồ Use Case Tổng quan (Use Case Diagram)](#2-so-do-use-case)
- [3. Danh mục Use Case Tổng hợp](#3-danh-muc-use-case)
- [4. Đặc tả Chi tiết Use Case](#4-dac-ta-chi-tiet)
    - [PHẦN 1: QUẢN LÝ NHÓM & THÀNH VIÊN](#phan-1-nhom-thanh-vien)
        - [UC01: Tạo nhóm mới](#uc01-tao-nhom)
        - [UC02: Tham gia nhóm bằng mã mời](#uc02-tham-gia-nhom)
        - [UC03: Quản lý mã mời (Lấy / Làm mới mã)](#uc03-quan-ly-ma-moi)
        - [UC04: Duyệt / Từ chối thành viên chờ](#uc04-duyet-thanh-vien)
        - [UC05: Chuyển quyền chủ nhóm](#uc05-chuyen-quyen-chu-nhom)
        - [UC06: Bàn giao thủ quỹ](#uc06-ban-giao-thu-quy)
        - [UC07: Mời thành viên rời nhóm](#uc07-moi-thanh-vien-roi-nhom)
        - [UC08: Tự rời nhóm](#uc08-tu-roi-nhom)
        - [UC09: Lưu trữ và mở lại nhóm](#uc09-luu-tru-mo-lai-nhom)
        - [UC10: Xoá nhóm](#uc10-xoa-nhom)
    - [PHẦN 2: QUẢN LÝ GIAO DỊCH & QUỸ NHÓM](#phan-2-giao-dich-quy)
        - [UC11: Ghi nhận chi tiêu nhóm (EXPENSE)](#uc11-ghi-chi-tieu)
        - [UC12: Ghi nhận đóng góp quỹ (CONTRIBUTION)](#uc12-dong-gop-quy)
        - [UC13: Duyệt hoặc từ chối giao dịch (Đơn lẻ & Hàng loạt)](#uc13-duyet-tu-choi-giao-dich)
        - [UC14: Chỉnh sửa giao dịch](#uc14-chinh-sua-giao-dich)
        - [UC15: Xoá giao dịch](#uc15-xoa-giao-dich)
        - [UC16: Quỹ trả tiền cho thành viên (REFUND)](#uc16-hoan-tra-tien-tui)
        - [UC18: Kiểm kê quỹ thực tế (Reconcile)](#uc18-kiem-ke-quy)
    - [PHẦN 3: BÁO CÁO & TRA CỨU](#phan-3-bao-cao-tra-cuu)
        - [UC19: Tra cứu thông tin nhóm & Số dư quỹ](#uc19-tra-cuu-thong-tin-nhom)
        - [UC20: Tra cứu lịch sử giao dịch nhóm](#uc20-tra-cuu-lich-su-giao-dich)
        - [UC21: Xem bảng phân bổ số dư & Số tiền cần nộp thêm](#uc21-xem-bang-so-du)
        - [UC22: Xem tổng quan tài chính nhóm](#uc22-xem-tong-quan-tai-chinh)
- [5. Ma trận Phân quyền Use Case (Traceability Matrix)](#5-ma-tran-phan-quyen)

---

## 1. Danh mục Tác nhân (Actors) <a id="1-danh-muc-tac-nhan"></a>

| Tác nhân                |   Ký hiệu   | Định danh kỹ thuật                                   | Mô tả vai trò & Phạm vi quyền hạn                                                                                                                                                                                             |
|:------------------------|:-----------:|:-----------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Người dùng hệ thống** |   `User`    | Tài khoản đã xác thực (`JWT`)                        | Bất kỳ người dùng nào đã đăng nhập, chưa tham gia nhóm hoặc đang tìm kiếm tham gia nhóm.                                                                                                                                      |
| **Thành viên Nhóm**     |  `Member`   | `group_members.role = 'MEMBER'`, `status = 'ACTIVE'` | Thành viên chính thức trong nhóm. Có quyền xem thông tin, ghi nhận chi tiêu/đóng góp, xem bảng phân bổ số dư và tự rời nhóm.                                                                                                  |
| **Thủ quỹ Nhóm**        | `Treasurer` | `group_funds.keepper_id = user.id`                   | Thành viên `ACTIVE` được chỉ định cầm tiền quỹ (mỗi nhóm đúng 1 thủ quỹ). Kế thừa toàn bộ quyền của `Member`, cộng thêm quyền: duyệt giao dịch, trả tiền cho thành viên (`REFUND`), kiểm kê quỹ.                              |
| **Chủ nhóm**            |   `Owner`   | `group_members.role = 'OWNER'`, `status = 'ACTIVE'`  | Người sáng lập hoặc nhận chuyển nhượng quyền quản trị nhóm (mỗi nhóm đúng 1 chủ nhóm). Kế thừa toàn bộ quyền của `Member` và `Treasurer`, cộng thêm toàn quyền quản trị cấu hình nhóm, thành viên, và xoá giao dịch/xoá nhóm. |

> 💡 **Bản chất kiến trúc:** Toàn hệ thống chỉ có **duy nhất 1 thực thể tài khoản người dùng** (`User` từ bảng `users`).
> Các tên gọi `Member`, `Treasurer`, `Owner` không phải loại tài khoản riêng biệt, mà là **các vai trò theo ngữ cảnh (
Contextual Roles)** của cùng một người dùng khi tham gia vào một nhóm cụ thể. Một người có thể là `Owner` nhóm này,
> nhưng chỉ là `Member` ở nhóm khác, hoặc chưa vào nhóm nào.

---

## 2. Sơ đồ Use Case Tổng quan (Use Case Diagram) <a id="2-so-do-use-case"></a>

```mermaid
flowchart LR
    subgraph ACTORS["Phân cấp Tác nhân & Kế thừa"]
        direction TB
        User["👤 Người dùng (User)<br/><i>(Chức năng cơ bản)</i>"]:::cUser
        Member["👤 Thành viên (Member)"]:::cMember
        Treasurer["👤 Thủ quỹ (Treasurer)"]:::cTreasurer
        Owner["👑 Chủ nhóm (Owner)"]:::cOwner
        User -.->|kế thừa| Member
        Member -.->|kế thừa| Treasurer
        Member -.->|kế thừa| Owner
        Treasurer -.->|kiêm quyền| Owner
    end

    subgraph SG1["📁 1. Quản lý Nhóm & Thành viên"]
        UC01["UC01: Tạo nhóm mới"]:::cGroup
        UC02["UC02: Tham gia nhóm bằng mã"]:::cGroup
        UC03["UC03: Xem & chia sẻ mã mời"]:::cGroup
        UC04["UC04: Duyệt / Từ chối thành viên PENDING"]:::cGroup
        UC04b["UC04b: Chủ nhóm thêm trực tiếp thành viên"]:::cGroup
        UC04c["UC04c: Đếm việc chờ duyệt (badge)"]:::cGroup
        UC05["UC05: Chuyển quyền chủ nhóm"]:::cGroup
        UC06["UC06: Bàn giao thủ quỹ"]:::cGroup
        UC07["UC07: Mời thành viên rời nhóm"]:::cGroup
        UC08["UC08: Tự rời nhóm"]:::cGroup
        UC09["UC09: Lưu trữ / Mở lại nhóm"]:::cGroup
        UC10["UC10: Xoá nhóm"]:::cGroup
    end

    subgraph SG2["💰 2. Quản lý Giao dịch & Quỹ nhóm"]
        UC11["UC11: Ghi chi tiêu (EXPENSE)"]:::cTxn
        UC12["UC12: Góp quỹ (CONTRIBUTION)"]:::cTxn
        UC13["UC13: Duyệt / Từ chối giao dịch"]:::cTxn
        UC14["UC14: Sửa giao dịch"]:::cTxn
        UC15["UC15: Xoá giao dịch"]:::cTxn
        UC16["UC16: Quỹ trả tiền cho thành viên (REFUND)"]:::cTxn
        UC18["UC18: Kiểm kê quỹ (Reconcile)"]:::cTxn
    end

    subgraph SG3["📊 3. Tra cứu & Báo cáo"]
        UC19["UC19: Tra cứu thông tin nhóm & Quỹ"]:::cReport
        UC20["UC20: Tra cứu lịch sử giao dịch"]:::cReport
        UC21["UC21: Xem bảng phân bổ số dư (/balances)"]:::cReport
        UC22["UC22: Xem tổng quan tài chính (/summary)"]:::cReport
    end

%% 1. Chức năng cơ bản của Người dùng
    User --> UC01
    User --> UC02
%% 2. Chức năng mở rộng của Thành viên (kế thừa User)
    Member --> UC03
    Member --> UC04c
    Member --> UC08
    Member --> UC11
    Member --> UC12
    Member --> UC14
    Member --> UC19
    Member --> UC20
    Member --> UC21
    Member --> UC22
%% 3. Chức năng mở rộng riêng của Thủ quỹ (kế thừa Member)
    Treasurer --> UC13
    Treasurer --> UC16
    Treasurer --> UC18
%% 4. Chức năng mở rộng riêng của Chủ nhóm (kế thừa Member & kiêm quyền Thủ quỹ)
    Owner --> UC04
    Owner --> UC04b
    Owner --> UC05
    Owner --> UC06
    Owner --> UC07
    Owner --> UC09
    Owner --> UC10
    Owner --> UC15
    classDef cUser fill: #334155, stroke: #94a3b8, stroke-width: 2px, color: #ffffff;
    classDef cMember fill: #0369a1, stroke: #38bdf8, stroke-width: 2px, color: #ffffff;
    classDef cTreasurer fill: #047857, stroke: #34d399, stroke-width: 2px, color: #ffffff;
    classDef cOwner fill: #b45309, stroke: #fbbf24, stroke-width: 2px, color: #ffffff;
    classDef cGroup fill: #312e81, stroke: #818cf8, stroke-width: 1.5px, color: #ffffff;
    classDef cTxn fill: #064e3b, stroke: #10b981, stroke-width: 1.5px, color: #ffffff;
    classDef cReport fill: #581c87, stroke: #c084fc, stroke-width: 1.5px, color: #ffffff;
    style ACTORS fill: none, stroke: #94a3b8, stroke-width: 1.5px;
    style SG1 fill: none, stroke: #6366f1, stroke-width: 2px;
    style SG2 fill: none, stroke: #10b981, stroke-width: 2px;
    style SG3 fill: none, stroke: #a855f7, stroke-width: 2px;

%% Phối màu đường nối theo từng Tác nhân (Line Colors)
    linkStyle 0,1,2,3 stroke: #94a3b8, stroke-width: 2px, stroke-dasharray: 4 4;
    linkStyle 4,5 stroke: #94a3b8, stroke-width: 2px;
    linkStyle 6,7,8,9,10,11,12,13,14,15 stroke: #38bdf8, stroke-width: 2px;
    linkStyle 16,17,18 stroke: #34d399, stroke-width: 2px;
    linkStyle 19,20,21,22,23,24,25,26 stroke: #fbbf24, stroke-width: 2px;
```

---

## 3. Danh mục Use Case Tổng hợp <a id="3-danh-muc-use-case"></a>

|   Mã UC   | Tên Use Case                              | Tác nhân chính                | API Endpoint gắn kết                                                                    |
|:---------:|:------------------------------------------|:------------------------------|:----------------------------------------------------------------------------------------|
| **UC01**  | Tạo nhóm mới                              | `User`                        | `POST /v1/groups`                                                                       |
| **UC02**  | Tham gia nhóm bằng mã mời                 | `User`                        | `POST /v1/groups/join`                                                                  |
| **UC03**  | Xem & chia sẻ mã mời                      | `Member`, `Owner`             | `GET /v1/groups/{id}` (trả trường `invite_code`)                                        |
| **UC04**  | Duyệt / Từ chối thành viên chờ            | `Owner`                       | `POST .../approve`, `POST .../approves`, `POST .../reject`, `POST .../rejects`          |
| **UC04b** | Chủ nhóm thêm trực tiếp thành viên        | `Owner`                       | `POST /v1/groups/{id}/members`                                                          |
| **UC04c** | Đếm việc chờ duyệt (badge)                | `Member`, `Owner`             | `GET /v1/groups/{id}/pending-count`                                                     |
| **UC05**  | Chuyển quyền chủ nhóm                     | `Owner`                       | `PUT /v1/groups/{id}/owner-role/{memberUserId}/`                                        |
| **UC06**  | Bàn giao thủ quỹ                          | `Owner`                       | `PUT /v1/groups/{id}/fund-kepper`                                                       |
| **UC07**  | Mời thành viên rời nhóm                   | `Owner`                       | `DELETE /v1/groups/{id}/members/{memberUserId}`                                         |
| **UC08**  | Tự rời nhóm                               | `Member`                      | `POST /v1/groups/{id}/leave`                                                            |
| **UC09**  | Lưu trữ / Mở lại nhóm                     | `Owner`                       | `POST /v1/groups/{id}/archive`, `POST .../unarchive`                                    |
| **UC10**  | Xoá nhóm                                  | `Owner`                       | `DELETE /v1/groups/{id}`                                                                |
| **UC11**  | Ghi nhận chi tiêu (`EXPENSE`)             | `Member`                      | `POST /v1/groups/{id}/transactions`                                                     |
| **UC12**  | Đóng góp tiền quỹ (`CONTRIBUTION`)        | `Member` (hoặc cựu TV)        | `POST /v1/groups/{id}/transactions`                                                     |
| **UC13**  | Duyệt / Từ chối giao dịch (Đơn lẻ & Bulk) | `Treasurer`, `Owner`          | `POST .../{tId}/confirm`, `.../reject`, `POST .../bulk-confirm`, `.../bulk-reject`      |
| **UC14**  | Chỉnh sửa giao dịch                       | `Member` (người tạo), `Owner` | `PUT /v1/groups/{id}/transactions/{tId}`                                                |
| **UC15**  | Xoá giao dịch                             | `Owner`                       | `DELETE /v1/groups/{id}/transactions/{tId}`                                             |
| **UC16**  | Quỹ trả tiền cho thành viên (`REFUND`)    | `Treasurer`, `Owner`          | `POST /v1/groups/{id}/transactions`, `PUT .../transactions/{tId}`                       |
| **UC18**  | Kiểm kê quỹ thực tế (`Reconcile`)         | `Treasurer`, `Owner`          | `POST /v1/groups/{id}/fund/reconcile`                                                   |
| **UC19**  | Tra cứu thông tin nhóm & Quỹ              | `Member`                      | `GET /v1/groups`, `GET /v1/groups/{id}` (nhúng quỹ trong `fund`)                        |
| **UC20**  | Tra cứu lịch sử giao dịch nhóm            | `Member`                      | `GET .../transactions`, `GET .../mine`, `GET .../pending`, `GET .../transactions/{tId}` |
| **UC21**  | Xem bảng phân bổ số dư & Số cần nộp       | `Member`                      | `GET /v1/groups/{id}/balances`                                                          |
| **UC22**  | Xem tổng quan tài chính nhóm              | `Member`                      | `GET /v1/groups/{id}/summary`                                                           |

---

## 4. Đặc tả Chi tiết Use Case <a id="4-dac-ta-chi-tiet"></a>

---

### PHẦN 1: QUẢN LÝ NHÓM & THÀNH VIÊN <a id="phan-1-nhom-thanh-vien"></a>

#### UC01: Tạo nhóm mới <a id="uc01-tao-nhom"></a>

- **Tác nhân:** `User`
- **Mô tả:** Khởi tạo một nhóm chung quỹ mới, khởi tạo cấu hình tính toán, tự động bổ nhiệm người tạo làm `OWNER` và
  thiết lập quỹ duy nhất của nhóm (số dư bắt đầu từ 0).
- **Tiền điều kiện:** Người dùng đã đăng nhập hệ thống (`Authorization: Bearer <token>`).
- **Hậu điều kiện:**
    - Bản ghi `groups` được tạo với mã mời 8 ký tự (không thời hạn).
    - Bản ghi `group_members` được tạo với `role = 'OWNER'`, `status = 'ACTIVE'`, `joined_at = now()`.
    - Bản ghi `group_funds` được tạo với `current_balance = 0`, `keepper_id` là người tạo.
- **Luồng chính (Success Flow):**
    1. Người dùng nhập tên nhóm, mô tả (tuỳ chọn), mục tiêu quỹ (tuỳ chọn), cờ tính thừa thiếu
       (`is_settlement_enabled`), cờ duyệt tự động (`is_join_without_confirm`), và tên quỹ.
    2. Hệ thống kiểm tra hợp lệ dữ liệu (Jakarta Validation).
    3. Trong 1 transaction CSDL, hệ thống:
        - Tạo nhóm `GroupEntity` và tự sinh mã mời 8 ký tự.
        - Tạo thành viên `GroupMemberEntity` gắn vai trò `OWNER`.
        - Tạo quỹ `FundEntity` số dư 0đ do người tạo giữ.
    4. Hệ thống phản hồi mã `201 Created` kèm thông tin chi tiết nhóm vừa tạo (`GroupDetailRes`).
- **Luồng ngoại lệ (Exception Flows):**
    - `400 INVALID_ARGUMENT`: Tên nhóm/tên quỹ bị rỗng, `target <= 0` hoặc vượt trần 999.999.999.999đ.

---

#### UC02: Tham gia nhóm bằng mã mời <a id="uc02-tham-gia-nhom"></a>

- **Tác nhân:** `User`
- **Mô tả:** Người dùng nhập mã mời gồm 8 ký tự để gia nhập nhóm chung (có mã là được xin vào, không giới hạn thời
  gian).
- **Tiền điều kiện:** Người dùng đã đăng nhập hệ thống, có mã mời 8 ký tự của nhóm.
- **Hậu điều kiện:** Người dùng trở thành thành viên `ACTIVE` (vào thẳng) hoặc `PENDING` (chờ chủ nhóm duyệt).
- **Luồng chính (Success Flow):**
    1. Người dùng gửi mã mời 8 ký tự lên hệ thống (`POST /v1/groups/join`).
    2. Hệ thống đối chiếu mã mời trong bảng `groups` (không kiểm tra thời hạn hết hạn).
    3. Hệ thống kiểm tra người dùng chưa ở trạng thái `ACTIVE` hoặc `PENDING` trong nhóm.
    4. Kiểm tra cờ `is_join_without_confirm` của nhóm:
        - Nếu `true`: Tạo `group_members` với `status = 'ACTIVE'`, `joined_at = now()`. Phản hồi `status: "ACTIVE"`.
        - Nếu `false`: Tạo `group_members` với `status = 'PENDING'`, `joined_at = NULL`. Phản hồi `status: "PENDING"`.
    5. Hệ thống trả về HTTP `200 OK` (chỉ trả trạng thái, không trả dữ liệu sổ nhóm bảo mật).
- **Luồng ngoại lệ (Exception Flows):**
    - `404 INVITE_CODE_INVALID`: Mã mời sai ký tự hoặc không tồn tại.
    - `409 GROUP_MEMBER_ALREADY_EXISTS`: Người dùng đã là thành viên hoặc đang có yêu cầu chờ duyệt.
    - `409 GROUP_ARCHIVED`: Nhóm đang ở trạng thái lưu trữ (`ARCHIVED`), không nhận thêm người.

---

#### UC03: Xem & chia sẻ mã mời <a id="uc03-quan-ly-ma-moi"></a>

- **Tác nhân:** `Member`, `Owner`
- **Mô tả:** Xem mã mời tham gia nhóm gồm 8 ký tự (được sinh tự động duy nhất khi tạo nhóm) để chia sẻ cho người khác.
- **Luồng chính (Success Flow):**
    1. Thành viên gọi API chi tiết nhóm (`GET /v1/groups/{id}`) hoặc danh sách nhóm (`GET /v1/groups`).
    2. Hệ thống kiểm tra quyền thành viên `ACTIVE`.
    3. Trả về mã mời trong trường `invite_code` của `GroupDetailRes` hoặc `GroupSummaryRes`.
    4. Thành viên sao chép và gửi mã mời cho người khác.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_MEMBER_REQUIRED`: Người gọi không phải thành viên `ACTIVE` của nhóm.

---

#### UC04: Duyệt / Từ chối thành viên chờ <a id="uc04-duyet-thanh-vien"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm duyệt hoặc từ chối yêu cầu tham gia của các thành viên đang `PENDING` (hỗ trợ cả đơn lẻ và hàng
  loạt).
- **Luồng chính - Duyệt:**
    - **Đơn lẻ:** `Owner` gọi `POST /v1/groups/{id}/members/{memberUserId}/approve`. Cập nhật `status = 'ACTIVE'`,
      `joined_at = now()`.
    - **Hàng loạt:** `Owner` gọi `POST /v1/groups/{id}/approves`. Chuyển toàn bộ thành viên đang `PENDING` sang
      `ACTIVE`.
- **Luồng chính - Từ chối:**
    - **Đơn lẻ:** `Owner` gọi `POST /v1/groups/{id}/members/{memberUserId}/reject`. Xoá bản ghi `PENDING` của thành
      viên.
    - **Hàng loạt:** `Owner` gọi `POST /v1/groups/{id}/rejects`. Xoá toàn bộ bản ghi `PENDING` của nhóm.
- **Luồng ngoại lệ:**
    - `403 GROUP_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `400/404`: Thành viên không tồn tại hoặc không ở trạng thái `PENDING`.

---

#### UC04b: Chủ nhóm thêm trực tiếp thành viên <a id="uc04b-them-thanh-vien-truc-tiep"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm thêm trực tiếp danh sách thành viên vào nhóm ở trạng thái `ACTIVE` mà không cần duyệt.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `POST /v1/groups/{id}/members` kèm mảng `member_ids` (`MemberAddReq`).
    2. Hệ thống kiểm tra quyền `OWNER` và nhóm không đang lưu trữ (`ARCHIVED`).
    3. Thêm các thành viên vào `group_members` với `status = 'ACTIVE'`, `role = 'MEMBER'`, `joined_at = now()`.
    4. Trả về `200 OK` kèm danh sách thành viên vừa thêm.
- **Luồng ngoại lệ:**
    - `403 GROUP_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `409 GROUP_ARCHIVED`: Nhóm đang lưu trữ.

---

#### UC04c: Đếm việc chờ duyệt (badge) <a id="uc04c-dem-viec-cho-duyet"></a>

- **Tác nhân:** `Member`, `Owner`
- **Mô tả:** Đếm số lượng giao dịch và thành viên đang chờ duyệt để hiển thị badge thông báo trên UI.
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `GET /v1/groups/{id}/pending-count`.
    2. Hệ thống kiểm tra vai trò:
        - Nếu là Owner: đếm cả giao dịch `PENDING` và thành viên `PENDING`.
        - Nếu là Thủ quỹ: đếm giao dịch `PENDING`, thành viên trả về 0.
        - Nếu là Thành viên thường: cả hai trả về 0.
    3. Trả về `200 OK` kèm `GroupPendingCountRes`.

---

#### UC05: Chuyển quyền chủ nhóm <a id="uc05-chuyen-quyen-chu-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm chuyển giao vai trò `OWNER` cho một thành viên `ACTIVE` khác trong nhóm ([rule.md](rule.md) quy
  tắc 9).
- **Tiền điều kiện:** Người nhận phải là thành viên `ACTIVE` của nhóm và khác người gọi.
- **Luồng chính (Success Flow):**
    1. `Owner` gửi yêu cầu `PUT /v1/groups/{id}/owner-role/{memberUserId}/`.
    2. Trong 1 transaction CSDL:
        - Cập nhật bản ghi của chủ cũ: `role = 'MEMBER'`.
        - Cập nhật bản ghi của người nhận: `role = 'OWNER'`.
    3. Quỹ nhóm không tự đổi người giữ (nếu chủ cũ đang giữ quỹ thì vẫn giữ cho đến khi bàn giao).
    4. Hệ thống phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 GROUP_NEW_OWNER_NOT_MEMBER`: Người nhận không phải là thành viên `ACTIVE`.
    - `400 GROUP_TRANSFER_TO_SELF`: Người nhận trùng với chủ nhóm hiện tại.
    - `409 GROUP_ARCHIVED`: Nhóm đang lưu trữ.

---

#### UC06: Bàn giao thủ quỹ <a id="uc06-ban-giao-thu-quy"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm bàn giao người giữ quỹ nhóm (`keepper_id`) sang một thành viên `ACTIVE` khác.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `PUT /v1/groups/{id}/fund-kepper` kèm `keepper_id` (`FundKepperUpdateReq`).
    2. Hệ thống xác thực người được giao quyền là thành viên `ACTIVE` của nhóm.
    3. Cập nhật `group_funds.keepper_id`.
    4. Trả về `200 OK` kèm thông tin quỹ mới (`FundRes`).
- **Luồng ngoại lệ (Exception Flows):**
    - `400 GROUP_FUND_HOLDER_NOT_MEMBER`: Người được bàn giao không phải thành viên `ACTIVE`.
    - `403 GROUP_OWNER_REQUIRED`: Người gọi không phải là chủ nhóm.

---

#### UC07: Mời thành viên rời nhóm <a id="uc07-moi-thanh-vien-roi-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm mời một thành viên `ACTIVE` ra khỏi nhóm.
- **Tiền điều kiện:**
    - Không thể tự mời chính mình rời (`409 GROUP_OWNER_NOT_REMOVABLE`).
    - Người bị mời rời **không được đang giữ quỹ** (`409 GROUP_TREASURER_TRANSFER_REQUIRED` — chủ nhóm phải dùng UC06 chuyển
      quỹ trước).
    - Nếu nhóm **bật tính thừa thiếu**: Nhóm không còn giao dịch `PENDING` và phần của người đó (`net_balance`) phải
      bằng 0 ([rule.md](rule.md) quy tắc 27).
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}/members/{memberUserId}`.
    2. Hệ thống kiểm tra đầy đủ các điều kiện tiền đề.
    3. Trong 1 transaction CSDL:
        - Cập nhật `group_members`: `status = 'REMOVED'`, `left_at = now()`.
        - Tự động chuyển toàn bộ giao dịch `PENDING` do người này tạo (`created_by`) sang `status = 'REJECTED'`.
    4. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `409 GROUP_TREASURER_TRANSFER_REQUIRED`: Thành viên bị mời đang giữ quỹ.
    - `409 GROUP_PENDING_TXN_EXIST`: Nhóm còn khoản chờ duyệt (khi bật tính thừa thiếu).
    - `409 GROUP_MEMBER_SHARE_NOT_ZERO`: Người bị mời còn nợ hoặc còn tiền trong quỹ (khi bật tính thừa thiếu).

---

#### UC08: Tự rời nhóm <a id="uc08-tu-roi-nhom"></a>

- **Tác nhân:** `Member`
- **Mô tả:** Thành viên chủ động rời khỏi nhóm chung quỹ.
- **Tiền điều kiện:**
    - Không phải là `OWNER` (`409 GROUP_OWNER_TRANSFER_REQUIRED` — phải dùng UC05 chuyển quyền trước).
    - Không đang giữ quỹ (`409 GROUP_TREASURER_TRANSFER_REQUIRED` — phải yêu cầu chủ nhóm bàn giao quỹ trước).
    - Nếu bật tính thừa thiếu: Phải tất toán phần trong quỹ về 0 (`net_balance == 0`) và không còn khoản `PENDING`. Nếu
      tắt tính thừa thiếu: Rời tự do.
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `POST /v1/groups/{id}/leave`.
    2. Hệ thống kiểm tra điều kiện tất toán và trạng thái vai trò.
    3. Trong 1 transaction CSDL:
        - Cập nhật bản ghi `group_members`: `status = 'LEFT'`, `left_at = now()`.
        - Hệ thống tự động quét và đánh dấu `status = 'REJECTED'` toàn bộ giao dịch `PENDING` do người này tạo
          (`created_by`).
    4. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `409 GROUP_OWNER_TRANSFER_REQUIRED`: Chủ nhóm cố rời nhóm mà chưa chuyển quyền.
    - `409 GROUP_TREASURER_TRANSFER_REQUIRED`: Thủ quỹ cố rời nhóm mà chưa bàn giao quỹ.
    - `409 GROUP_MEMBER_SHARE_NOT_ZERO`: Phần trong quỹ khác 0 (khi bật tính thừa thiếu).

---

#### UC09: Lưu trữ và mở lại nhóm <a id="uc09-luu-tru-mo-lai-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Đưa nhóm vào trạng thái lưu trữ (chỉ xem, đóng băng mọi thao tác ghi) hoặc mở lại hoạt động bình thường.
- **Luồng chính - Lưu trữ (Archive):**
    1. `Owner` gọi `POST /v1/groups/{id}/archive`.
    2. Kiểm tra nhóm đang `ACTIVE` và **không còn khoản PENDING** (`409 GROUP_PENDING_TXN_EXIST`).
    3. Cập nhật `groups.status = 'ARCHIVED'`. Trả về `200 OK`.
- **Luồng chính - Mở lại (Unarchive):**
    1. `Owner` gọi `POST /v1/groups/{id}/unarchive`.
    2. Cập nhật `groups.status = 'ACTIVE'`. Trả về `200 OK`.
- **Luồng ngoại lệ:**
    - `403 GROUP_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `409 GROUP_PENDING_TXN_EXIST`: Còn khoản chờ duyệt khi lưu trữ.

---

#### UC10: Xoá nhóm <a id="uc10-xoa-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Xoá mềm nhóm khi số dư và phân bổ đã được giải quyết xong.
- **Tiền điều kiện ([rule.md](rule.md) quy tắc 23):**
    - Nhóm không còn bất kỳ khoản giao dịch nào ở trạng thái `PENDING`.
    - Số dư quỹ `current_balance == 0`.
    - Nếu bật tính thừa thiếu: Phần trong quỹ của toàn bộ thành viên (kể cả cựu thành viên đã rời) đều phải bằng 0.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}`.
    2. Hệ thống xác thực 3 điều kiện tiền đề trên.
    3. Trong 1 transaction CSDL:
        - Cập nhật `groups.status = 'DELETED'`.
    4. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `409 GROUP_PENDING_TXN_EXIST`: Còn khoản chờ duyệt.
    - `409 GROUP_DELETE_BALANCE_NOT_ZERO`: Quỹ còn tiền hoặc còn người có số dư thừa/thiếu.

---

### PHẦN 2: QUẢN LÝ GIAO DỊCH & QUỸ NHÓM <a id="phan-2-giao-dich-quy"></a>

#### UC11: Ghi nhận chi tiêu nhóm (EXPENSE) <a id="uc11-ghi-chi-tieu"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Ghi nhận một khoản tiền đã chi tiêu cho nhóm. Nguồn tiền có thể từ Quỹ nhóm (`FUND`) hoặc tiền túi cá nhân
  chi hộ (`PERSONAL`).
- **Tiền điều kiện:** Nhóm đang `ACTIVE`; `occurred_at <= now()`; có chọn danh mục hệ thống loại chi; người trả và người
  tham gia phải có mặt tại thời điểm `occurred_at`.
- **Luồng chính (Success Flow):**
    1. Thành viên gửi thông tin chi tiêu (`POST /v1/groups/{id}/transactions`): `amount`, `money_source`, `category_id`,
       `transactor_id`, `occurred_at`, `participants` (nếu có).
    2. Hệ thống kiểm tra toàn vẹn nghiệp vụ:
        - Kiểm tra người trả và người tham gia hợp lệ tại `occurred_at` ([pipeline.md](pipeline.md) mục 1).
        - Nếu không gửi danh sách người tham gia (hoặc gửi rỗng): hệ thống tự động chia đều cho toàn bộ thành viên nhóm có mặt tại thời điểm `occurred_at` và tính `share_amount` cho từng người (`splitEvenly`, Migration `V16`).
        - Nếu có gửi danh sách người tham gia cụ thể: kiểm tra tổng `share_amount` phải bằng đúng `amount` của giao dịch (`GROUP_TXN_PARTICIPANTS_SUM_MISMATCH`).
    3. Xác định trạng thái duyệt:
        - Nếu người ghi là `Treasurer` hoặc `Owner` → Gán `status = 'CONFIRMED'`, `reviewed_by = userId`,
          `reviewed_at = now()`. Nếu `money_source == 'FUND'`, khoá quỹ và trừ số dư
          `current_balance = current_balance - amount`.
        - Nếu người ghi là thành viên thường → Gán `status = 'PENDING'`, chưa tác động đến quỹ.
    4. Lưu bản ghi `group_transactions` và các dòng `group_transaction_participants` (luôn có `share_amount > 0`).
    5. Cập nhật delta biến động tài chính của các thành viên vào bảng tích luỹ `group_member_balances` qua `memberBalanceService.applyDelta` (Migration `V18`).
    6. Trả về `201 Created` kèm `GroupTransactionDetailRes`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 GROUP_TXN_DATE_IN_FUTURE`: Thời điểm phát sinh ở tương lai.
    - `400 GROUP_TXN_PAYER_NOT_MEMBER` / `GROUP_TXN_PARTICIPANT_NOT_MEMBER`: Người trả hoặc người chia tiền không có mặt tại thời điểm giao
      dịch.
    - `400 GROUP_TXN_SYSTEM_CATEGORY_REQUIRED`: Danh mục không phải danh mục hệ thống loại chi.
    - `400 GROUP_TXN_PARTICIPANTS_SUM_MISMATCH`: Tổng tiền tự nhập không khớp với số tiền chi.
    - `409 GROUP_ARCHIVED`: Nhóm đang lưu trữ.

---

#### UC12: Ghi nhận đóng góp quỹ (CONTRIBUTION) <a id="uc12-dong-gop-quy"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner` (hoặc cựu thành viên nộp bù)
- **Mô tả:** Ghi nhận một thành viên nộp tiền cá nhân vào quỹ nhóm.
- **Tiền điều kiện:** `money_source = 'PERSONAL'`; không có danh mục; không có người tham gia (`participants: []`).
- **Luồng chính (Success Flow):**
    1. Thành viên gửi yêu cầu nộp quỹ (`type = 'CONTRIBUTION'`).
    2. Người nộp/đối ứng (`transactor_id`) phải là thành viên có mặt tại `occurred_at`, HOẶC là cựu thành viên đang có
       phần âm trong
       quỹ (nộp bù theo Cách A).
    3. Phân định trạng thái:
        - Thủ quỹ/Chủ nhóm tự ghi → `CONFIRMED` ngay, khoá quỹ và cộng số dư
          `current_balance = current_balance + amount`.
        - Thành viên tự ghi → `PENDING` (chờ thủ quỹ nhận được tiền thật rồi duyệt).
    4. Lưu bản ghi, cập nhật biến động vào `group_member_balances` (Migration `V18`) và trả về `201 Created`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 GROUP_TXN_MONEY_SOURCE_INVALID`: Góp quỹ mà chọn nguồn tiền `FUND`.
    - `400 GROUP_TXN_PARTICIPANTS_NOT_ALLOWED`: Góp quỹ có gắn người tham gia chia tiền.

---

#### UC13: Duyệt hoặc từ chối giao dịch (Đơn lẻ & Hàng loạt) <a id="uc13-duyet-tu-choi-giao-dich"></a>

- **Tác nhân:** `Treasurer`, `Owner`
- **Mô tả:** Thủ quỹ hoặc Chủ nhóm xác nhận (`CONFIRMED`) tiền đã vào/ra thực tế, hoặc từ chối (`REJECTED`) một hoặc
  nhiều khoản giao dịch đang chờ duyệt.
- **Tiền điều kiện:** Các giao dịch phải đang ở trạng thái `PENDING`.
- **Luồng chính - Duyệt đơn lẻ / Hàng loạt:**
    1. Tác nhân gọi `POST .../{tId}/confirm` hoặc `POST .../transactions/bulk-confirm`.
    2. Trong 1 transaction CSDL:
        - Kiểm tra các giao dịch đang `PENDING`.
        - Chuyển `status = 'CONFIRMED'`, gán `reviewed_by = userId`, `reviewed_at = now()`.
        - Cập nhật delta biến động tài chính của các thành viên vào bảng `group_member_balances` qua `memberBalanceService.applyDelta` (Migration `V18`).
        - **Khoá dòng quỹ (`FOR UPDATE`)** và áp delta chênh lệch lên quỹ:
            - `EXPENSE (FUND)`: Giảm quỹ.
            - `CONTRIBUTION`: Tăng quỹ.
            - Với duyệt hàng loạt: Gộp delta số dư quỹ và gộp biến động các thành viên rồi cập nhật 1 lần duy nhất cho toàn bộ lô.
    3. Phản hồi `200 OK`.
- **Luồng chính - Từ chối đơn lẻ / Hàng loạt:**
    1. Tác nhân gọi `POST .../{tId}/reject` hoặc `POST .../transactions/bulk-reject`.
    2. Chuyển `status = 'REJECTED'`, gán thông tin người duyệt. Quỹ và bảng số dư thành viên không bị tác động.
    3. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_TREASURER_REQUIRED`: Người duyệt không phải Thủ quỹ hoặc Chủ nhóm.
    - `409 GROUP_TXN_NOT_PENDING`: Giao dịch đã được duyệt hoặc bị từ chối trước đó.

---

#### UC14: Chỉnh sửa giao dịch <a id="uc14-chinh-sua-giao-dich"></a>

- **Tác nhân:** `Member` (người tạo), `Owner`
- **Mô tả:** Sửa thông tin giao dịch (`EXPENSE`, `CONTRIBUTION`, `REFUND`) qua Strategy Pattern.
- **Quy tắc phân quyền:** Người tạo (`created_by`) được sửa khoản của mình; `Owner` được sửa mọi khoản.
- **Luồng chính (Success Flow):**
    1. Tác nhân gửi thông tin cập nhật (`PUT /v1/groups/{id}/transactions/{tId}`).
    2. Trong 1 transaction CSDL:
        - Chụp ảnh hưởng cũ của giao dịch: `before = BalanceCalculator.effectOf(txn)`.
        - Nếu khoản đang `CONFIRMED`: Hoàn tác ảnh hưởng số tiền cũ lên quỹ.
        - Điều phối tới `GTransactionUpdate` phụ trách loại giao dịch đó để cập nhật thông tin và phân bổ lại danh sách người tham gia (kèm `share_amount > 0`).
        - Kiểm tra người sửa:
            - Nếu là `Treasurer` hoặc `Owner` → Khoản giữ nguyên `CONFIRMED`, tính ảnh hưởng mới lên quỹ.
            - Nếu là `Member` thường → Khoản chuyển về `PENDING`, chờ duyệt lại.
            - Nếu khoản đang `REJECTED` được người tạo sửa lại → Tự động quay về `PENDING`.
        - Cập nhật chênh lệch delta giữa `before` và ảnh hưởng mới vào `group_member_balances` (Migration `V18`).
        - Gửi sự kiện `FundBalanceChangedEvent` nếu có chênh lệch delta số dư quỹ.
    3. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_TXN_EDIT_FORBIDDEN`: Thành viên thường cố sửa giao dịch của người khác.
    - `400 GROUP_TXN_TYPE_NOT_ALLOWED`: Cố ý đổi `type` sang loại khác hoặc sửa giao dịch kiểm kê (`ADJUSTMENT_*`).

---

#### UC15: Xoá giao dịch <a id="uc15-xoa-giao-dich"></a>

- **Tác nhân:** `Owner` (độc quyền)
- **Mô tả:** Xoá mềm một khoản giao dịch khỏi sổ nhóm ([rule.md](rule.md) quy tắc 21).
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}/transactions/{tId}`.
    2. Trong 1 transaction CSDL:
        - Chụp ảnh hưởng cũ `before = BalanceCalculator.effectOf(txn)`.
        - Gán `deleted_at = now()` (xoá mềm).
        - Nếu khoản đang `CONFIRMED`: Khoá quỹ và hoàn tác toàn bộ ảnh hưởng tài chính của khoản đó lên
          `group_funds.current_balance`.
        - Hoàn tác ảnh hưởng tài chính trong `group_member_balances` qua `memberBalanceService.applyDelta(groupId, before, NO_EFFECT)` (Migration `V18`).
    3. Trả về `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_OWNER_REQUIRED`: Thành viên thường hoặc thủ quỹ cố thực hiện xoá giao dịch.

---

#### UC16: Quỹ trả tiền cho thành viên (REFUND) <a id="uc16-hoan-tra-tien-tui"></a>

- **Tác nhân:** `Treasurer`, `Owner`
- **Mô tả:** Quỹ nhóm chi trả tiền cho một thành viên qua giao dịch loại `REFUND`: hoàn tiền túi người đó đã bỏ ra chi
  hộ nhóm, hoặc trả lại tiền người đó đã góp (góp dư, tất toán rời nhóm, giải tán nhóm). Số UC17 (rút lại tiền góp) đã
  gộp vào use case này (29/09/2026), số UC không đánh lại.
- **Tiền điều kiện:** Người nhận phải là thành viên nhóm; `amount <= net_balance` (không được trả vượt phần người đó
  còn trong quỹ), áp dụng cho cả bật và tắt tính thừa thiếu.
- **Hậu điều kiện:**
    - Sinh bản ghi `type = 'REFUND'`, `money_source = 'FUND'`, `status = 'CONFIRMED'` ngay.
    - Trừ số dư quỹ `current_balance = current_balance - amount`.
    - Phần trong quỹ (`net_balance`) của người nhận giảm tương ứng, cập nhật vào `group_member_balances`.
- **Luồng chính (Success Flow):**
    1. Tác nhân gửi yêu cầu tạo giao dịch (`POST /v1/groups/{id}/transactions` với `type = 'REFUND'`,
       `money_source = 'FUND'`, `transactor_id` là người nhận).
    2. Hệ thống kiểm tra điều kiện giới hạn số tiền (`RefundTransaction`).
    3. Khoá dòng quỹ, trừ tiền quỹ, cập nhật delta vào `group_member_balances`, ghi nhận giao dịch `CONFIRMED`.
    4. Trả về `201 Created`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_TREASURER_REQUIRED`: Không có quyền thủ quỹ/chủ nhóm.
    - `400 GROUP_TXN_REFUND_EXCEEDS_BALANCE`: Số tiền trả vượt quá số còn lại trong quỹ của người nhận.

---

#### UC18: Kiểm kê quỹ thực tế (Reconcile) <a id="uc18-kiem-ke-quy"></a>

- **Tác nhân:** `Treasurer`, `Owner`
- **Mô tả:** Thủ quỹ/Chủ nhóm đếm tiền mặt thực tế và đối soát với số dư trên sổ app mà không làm sai lệch báo cáo chi
  tiêu.
- **Luồng chính (Success Flow):**
    1. Tác nhân gửi số tiền thực đếm (`actual_balance`) và danh sách loại trừ nếu có
       (`POST /v1/groups/{id}/fund/reconcile`).
    2. Trong 1 transaction CSDL:
        - **Khoá bi quan dòng quỹ (`FOR UPDATE`)** để lấy `current_balance` chuẩn xác nhất.
        - Tính độ lệch: `difference = actual_balance - current_balance`.
        - Nếu `difference == 0`: Không sinh giao dịch, trả về thông báo khớp tiền.
        - Nếu `difference > 0`: Tạo giao dịch `ADJUSTMENT_UP` với `amount = difference`.
        - Nếu `difference < 0`: Tạo giao dịch `ADJUSTMENT_DOWN` với `amount = abs(difference)`.
        - Giao dịch có `money_source = 'FUND'`, `status = 'CONFIRMED'` ngay, không gắn danh mục.
        - Người chịu độ lệch: Hệ thống tự động chia đều cho toàn bộ thành viên nhóm có mặt (hoặc thành viên không bị loại trừ) và tạo các bản ghi `group_transaction_participants` với `share_amount` tương ứng (`splitEvenly`, Migration `V16`).
        - Cập nhật delta vào bảng tích luỹ `group_member_balances` (Migration `V18`).
        - Cập nhật số dư quỹ bằng câu lệnh cộng dồn nguyên tử: `SET current_balance = current_balance + :difference`
          (không ghi đè).
    3. Trả về `200 OK` kèm kết quả đối soát (`GroupFundReconcileRes`).
- **Luồng ngoại lệ (Exception Flows):**
    - `403 GROUP_TREASURER_REQUIRED`: Không có quyền thủ quỹ hoặc chủ nhóm.
    - `400 GROUP_TXN_DATE_IN_FUTURE`: Thời điểm kiểm kê ở tương lai.

---

### PHẦN 3: BÁO CÁO & TRA CỨU <a id="phan-3-bao-cao-tra-cuu"></a>

#### UC19: Tra cứu thông tin nhóm & Quỹ <a id="uc19-tra-cuu-thong-tin-nhom"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Xem danh sách các nhóm đang tham gia, chi tiết cấu hình nhóm, danh sách thành viên và thông tin quỹ nhóm
  (nhúng sẵn trong chi tiết nhóm).
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `GET /v1/groups` để lấy tóm tắt các nhóm đang `ACTIVE` (hoặc `ARCHIVED`).
    2. Gọi `GET /v1/groups/{id}` để xem chi tiết nhóm, danh sách thành viên, vai trò của mình và thông tin quỹ nhóm
       (`GroupDetailRes.fund`).
- **Luồng ngoại lệ:**
    - `403 GROUP_MEMBER_REQUIRED`: Người gọi không phải là thành viên `ACTIVE` của nhóm.

---

#### UC20: Tra cứu lịch sử giao dịch nhóm <a id="uc20-tra-cuu-lich-su-giao-dich"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Tra cứu danh sách giao dịch có phân trang, lọc đa tiêu chí (theo nguồn tiền, loại, trạng thái duyệt, khoảng
  ngày, người chi), xem giao dịch của chính mình hoặc giao dịch đang chờ duyệt.
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `GET /v1/groups/{id}/transactions` kèm tham số lọc và phân trang (`page`, `size`) để xem lịch sử
       chung.
    2. Hoặc gọi `GET /v1/groups/{id}/transactions/mine` để xem toàn bộ giao dịch do mình tạo (kể cả khoản `PENDING`).
    3. Thủ quỹ / Chủ nhóm gọi `GET /v1/groups/{id}/transactions/pending` để xem danh sách các khoản cần duyệt.
    4. Gọi `GET .../transactions/{tId}` để xem chi tiết giao dịch.
    5. Gọi `GET .../transactions/{tId}/participants` để lấy chi tiết danh sách người cùng chịu khoản chi và số tiền mỗi người chịu (`share_amount`).

---

#### UC21: Xem bảng phân bổ số dư & Số tiền cần nộp thêm <a id="uc21-xem-bang-so-du"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Xem bảng tính phân bổ tài chính của từng người trong nhóm (`GET /v1/groups/{id}/balances`).
- **Quy tắc hiển thị ([pipeline.md](pipeline.md) mục 4 & 5):**
    - **Nếu bật tính thừa thiếu (`is_settlement_enabled = true`):**
        - Trả về chi tiết: `total_paid_out_of_pocket`, `total_refunded`, `total_share_amount`,
          `net_balance` (phần trong quỹ), và `needed_contribution` (số tiền cần nộp thêm).
        - Thành viên đã rời (`LEFT`/`REMOVED`): Nếu `net_balance == 0` thì ẩn; nếu `net_balance != 0` thì hiển thị để
          nhóm xử lý nộp bù hoặc tự gánh.
    - **Nếu tắt tính thừa thiếu (`is_settlement_enabled = false`):**
        - Không ai cần trả ai. Ứng dụng chỉ hiển thị mục tiêu `target`, số dư quỹ `fund_balance` và số còn lại của
          mỗi người trong quỹ `net_balance`; `total_needed_contribution` là `null`.
    - `target` chỉ để xem tiến độ gom quỹ; `needed_contribution` luôn bằng `|net_balance|` nếu `net_balance < 0`, ngược
      lại `0`.

---

#### UC22: Xem tổng quan tài chính nhóm <a id="uc22-xem-tong-quan-tai-chinh"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Xem thẻ thống kê vĩ mô ở trang chủ nhóm (`GET /v1/groups/{id}/summary?month=YYYY-MM`).
- **Luồng chính (Success Flow):**
    1. Thành viên gọi API tóm tắt tài chính (mặc định lấy tháng hiện tại theo giờ UTC+7).
    2. Hệ thống tính toán và trả về:
        - Số dư quỹ hiện có (`current_balance`).
        - Mục tiêu gom quỹ (`target`).
        - Tổng chi tiêu trong kỳ (`total_expense` — chỉ tính các khoản `EXPENSE` đã `CONFIRMED`).
        - Tổng tiền đã góp vào quỹ (`total_contribution`).

---

## 5. Ma trận Phân quyền Use Case (Traceability Matrix) <a id="5-ma-tran-phan-quyen"></a>

|   Mã UC   | Tên Use Case                           | Khách (`User`) | Thành viên (`Member`) | Thủ quỹ (`Treasurer`) | Chủ nhóm (`Owner`) | Nhóm lưu trữ (`ARCHIVED`) |
|:---------:|:---------------------------------------|:--------------:|:---------------------:|:---------------------:|:------------------:|:-------------------------:|
| **UC01**  | Tạo nhóm mới                           |       ✅       |          ✅           |          ✅           |         ✅         |             —             |
| **UC02**  | Tham gia nhóm bằng mã mời              |       ✅       |          ❌           |          ❌           |         ❌         |         ❌ (Chặn)         |
| **UC03**  | Xem & chia sẻ mã mời                   |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC04**  | Duyệt / Từ chối thành viên chờ         |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC04b** | Chủ nhóm thêm trực tiếp thành viên     |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC04c** | Đếm việc chờ duyệt (badge)             |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC05**  | Chuyển quyền chủ nhóm                  |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC06**  | Bàn giao thủ quỹ                       |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC07**  | Mời thành viên rời nhóm                |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC08**  | Tự rời nhóm                            |       ❌       |          ✅           |   ❌ (Phải chuyển)    |  ❌ (Phải chuyển)  |         ❌ (Chặn)         |
| **UC09**  | Lưu trữ / Mở lại nhóm                  |       ❌       |          ❌           |          ❌           |         ✅         |        ✅ (Mở lại)        |
| **UC10**  | Xoá nhóm                               |       ❌       |          ❌           |          ❌           |         ✅         |       ✅ (Cho phép)       |
| **UC11**  | Ghi nhận chi tiêu (`EXPENSE`)          |       ❌       |     ✅ (PENDING)      |    ✅ (CONFIRMED)     |   ✅ (CONFIRMED)   |         ❌ (Chặn)         |
| **UC12**  | Đóng góp tiền quỹ (`CONTRIBUTION`)     |       ❌       |     ✅ (PENDING)      |    ✅ (CONFIRMED)     |   ✅ (CONFIRMED)   |         ❌ (Chặn)         |
| **UC13**  | Duyệt / Từ chối giao dịch              |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC14**  | Sửa giao dịch                          |       ❌       |     ✅ (Của mình)     |     ✅ (Của mình)     |    ✅ (Tất cả)     |         ❌ (Chặn)         |
| **UC15**  | Xoá giao dịch                          |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC16**  | Quỹ trả tiền cho thành viên (`REFUND`) |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC18**  | Kiểm kê quỹ thực tế (`Reconcile`)      |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC19**  | Tra cứu thông tin nhóm & Quỹ           |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC20**  | Tra cứu lịch sử giao dịch nhóm         |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC21**  | Xem bảng phân bổ số dư & Cần nộp       |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC22**  | Xem tổng quan tài chính nhóm           |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
