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
| **Thủ quỹ Nhóm**        | `Treasurer` | `group_funds.held_by_user_id = user.id`              | Thành viên `ACTIVE` được chỉ định cầm tiền quỹ (mỗi nhóm đúng 1 thủ quỹ). Kế thừa toàn bộ quyền của `Member`, cộng thêm quyền: duyệt giao dịch, trả tiền cho thành viên (`REFUND`), kiểm kê quỹ.                              |
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
        UC03["UC03: Quản lý mã mời"]:::cGroup
        UC04["UC04: Duyệt / Từ chối thành viên PENDING"]:::cGroup
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
    Owner --> UC03
    Owner --> UC04
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
    linkStyle 6,7,8,9,10,11,12,13 stroke: #38bdf8, stroke-width: 2px;
    linkStyle 14,15,16,17 stroke: #34d399, stroke-width: 2px;
    linkStyle 18,19,20,21,22,23,24,25 stroke: #fbbf24, stroke-width: 2px;
```

---

## 3. Danh mục Use Case Tổng hợp <a id="3-danh-muc-use-case"></a>

|  Mã UC   | Tên Use Case                              | Tác nhân chính                | API Endpoint gắn kết                                                  |
|:--------:|:------------------------------------------|:------------------------------|:----------------------------------------------------------------------|
| **UC01** | Tạo nhóm mới                              | `User`                        | `POST /v1/groups`                                                     |
| **UC02** | Tham gia nhóm bằng mã mời                 | `User`                        | `POST /v1/groups/join`                                                |
| **UC03** | Lấy / Làm mới mã mời                      | `Owner`                       | `POST /v1/groups/{id}/invite-code`                                    |
| **UC04** | Duyệt / Từ chối thành viên chờ            | `Owner`                       | `POST /members/{uId}/approve`, `DELETE /members/{uId}`                |
| **UC05** | Chuyển quyền chủ nhóm                     | `Owner`                       | `POST /v1/groups/{id}/transfer-ownership`                             |
| **UC06** | Bàn giao thủ quỹ                          | `Owner`                       | `PATCH /v1/groups/{id}/fund`                                          |
| **UC07** | Mời thành viên rời nhóm                   | `Owner`                       | `DELETE /v1/groups/{id}/members/{uId}`                                |
| **UC08** | Tự rời nhóm                               | `Member`                      | `POST /v1/groups/{id}/leave`                                          |
| **UC09** | Lưu trữ / Mở lại nhóm                     | `Owner`                       | `POST /groups/{id}/archive`, `POST .../unarchive`                     |
| **UC10** | Xoá nhóm                                  | `Owner`                       | `DELETE /v1/groups/{id}`                                              |
| **UC11** | Ghi nhận chi tiêu (`EXPENSE`)             | `Member`                      | `POST /v1/groups/{id}/transactions`                                   |
| **UC12** | Đóng góp tiền quỹ (`CONTRIBUTION`)        | `Member` (hoặc cựu TV)        | `POST /v1/groups/{id}/transactions`                                   |
| **UC13** | Duyệt / Từ chối giao dịch (Đơn lẻ & Bulk) | `Treasurer`, `Owner`          | `POST .../{tId}/confirm`, `.../reject`, `bulk-confirm`, `bulk-reject` |
| **UC14** | Chỉnh sửa giao dịch                       | `Member` (người tạo), `Owner` | `PUT /v1/groups/{id}/transactions/{tId}`                              |
| **UC15** | Xoá giao dịch                             | `Owner`                       | `DELETE /v1/groups/{id}/transactions/{tId}`                           |
| **UC16** | Quỹ trả tiền cho thành viên (`REFUND`)    | `Treasurer`, `Owner`          | `POST /v1/groups/{id}/transactions`, `PUT .../transactions/{tId}`     |
| **UC18** | Kiểm kê quỹ thực tế (`Reconcile`)         | `Treasurer`, `Owner`          | `POST /v1/groups/{id}/fund/reconcile`                                 |
| **UC19** | Tra cứu thông tin nhóm & Quỹ              | `Member`                      | `GET /v1/groups`, `GET /groups/{id}`, `GET .../fund`                  |
| **UC20** | Tra cứu lịch sử giao dịch nhóm            | `Member`                      | `GET /v1/groups/{id}/transactions`, `GET .../transactions/{tId}`      |
| **UC21** | Xem bảng phân bổ số dư & Số cần nộp       | `Member`                      | `GET /v1/groups/{id}/balances`                                        |
| **UC22** | Xem tổng quan tài chính nhóm              | `Member`                      | `GET /v1/groups/{id}/summary`                                         |

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
    - Bản ghi `group_funds` được tạo với `current_balance = 0`, `status = 'ACTIVE'`, `held_by_user_id` là người tạo.
- **Luồng chính (Success Flow):**
    1. Người dùng nhập tên nhóm, mô tả (tuỳ chọn), mục tiêu quỹ (tuỳ chọn), cờ tính thừa thiếu
       (`is_settlement_enabled`), cờ duyệt tự động (`is_join_without_confirm`), và tên quỹ.
    2. Hệ thống kiểm tra hợp lệ dữ liệu (Jakarta Validation).
    3. Trong 1 transaction CSDL, hệ thống:
        - Tạo nhóm `GroupEntity` và tự sinh mã mời 8 ký tự.
        - Tạo thành viên `GroupMemberEntity` gắn vai trò `OWNER`.
        - Tạo quỹ `GroupFundEntity` số dư 0đ do người tạo giữ.
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
    - `409 ALREADY_IN_GROUP`: Người dùng đã là thành viên hoặc đang có yêu cầu chờ duyệt.
    - `409 GROUP_ARCHIVED`: Nhóm đang ở trạng thái lưu trữ (`ARCHIVED`), không nhận thêm người.

---

#### UC03: Quản lý mã mời (Lấy / Làm mới) <a id="uc03-quan-ly-ma-moi"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm lấy mã mời hiện tại hoặc chủ động thu hồi mã cũ để sinh mã mời mới.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `POST /v1/groups/{id}/invite-code?regenerate=true|false`.
    2. Hệ thống kiểm tra quyền `OWNER` của người gọi.
    3. Nếu `regenerate=false`: Trả về mã mời hiện tại của nhóm.
    4. Nếu `regenerate=true`: Sinh chuỗi 8 ký tự ngẫu nhiên mới, cập nhật bảng `groups`.
    5. Trả về mã `200 OK` kèm `invite_code`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `409 GROUP_ARCHIVED`: Nhóm đang lưu trữ.

---

#### UC04: Duyệt / Từ chối thành viên chờ <a id="uc04-duyet-thanh-vien"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm duyệt người đang `PENDING` vào nhóm chính thức, hoặc từ chối yêu cầu.
- **Luồng chính - Duyệt (Approve):**
    1. `Owner` gọi `POST /v1/groups/{id}/members/{userId}/approve`.
    2. Hệ thống kiểm tra bản ghi có `status = 'PENDING'`.
    3. Cập nhật `status = 'ACTIVE'`, `joined_at = now()`. Trả về `200 OK`.
- **Luồng chính - Từ chối (Reject):**
    1. `Owner` gọi `DELETE /v1/groups/{id}/members/{userId}` cho người `PENDING`.
    2. Hệ thống **xoá hẳn dòng** trong `group_members` (vì người này chưa từng vào nhóm, không cần lưu vết theo
       `ck_gm_dates`). Trả về `200 OK`.
- **Luồng ngoại lệ:**
    - `403 FORBIDDEN_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `400/404`: Thành viên không tồn tại hoặc không ở trạng thái `PENDING`.

---

#### UC05: Chuyển quyền chủ nhóm <a id="uc05-chuyen-quyen-chu-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm chuyển giao vai trò `OWNER` cho một thành viên `ACTIVE` khác trong nhóm ([rule.md](rule.md) quy
  tắc 9).
- **Tiền điều kiện:** Người nhận phải là thành viên `ACTIVE` của nhóm và khác người gọi.
- **Luồng chính (Success Flow):**
    1. `Owner` gửi yêu cầu kèm `new_owner_user_id` (`POST /v1/groups/{id}/transfer-ownership`).
    2. Trong 1 transaction CSDL:
        - Cập nhật bản ghi của chủ cũ: `role = 'MEMBER'`.
        - Cập nhật bản ghi của người nhận: `role = 'OWNER'`.
    3. Quỹ nhóm không tự đổi người giữ (nếu chủ cũ đang giữ quỹ thì vẫn giữ cho đến khi bàn giao).
    4. Hệ thống phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 NEW_OWNER_NOT_MEMBER`: Người nhận không phải là thành viên `ACTIVE`.
    - `400 CANNOT_TRANSFER_TO_SELF`: Người nhận trùng với chủ nhóm hiện tại.
    - `409 GROUP_ARCHIVED`: Nhóm đang lưu trữ.

---

#### UC06: Bàn giao thủ quỹ <a id="uc06-ban-giao-thu-quy"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm đổi người giữ quỹ nhóm sang một thành viên `ACTIVE` khác, hoặc đổi tên quỹ.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `PATCH /v1/groups/{id}/fund` kèm `held_by_user_id`.
    2. Hệ thống xác thực người được giao quyền là thành viên `ACTIVE` của nhóm.
    3. Cập nhật `group_funds.held_by_user_id`.
    4. Trả về `200 OK` kèm thông tin quỹ mới.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 HOLDER_NOT_MEMBER`: Người được bàn giao không phải thành viên `ACTIVE`.
    - `403 FORBIDDEN_OWNER_REQUIRED`: Người gọi không phải là chủ nhóm.

---

#### UC07: Mời thành viên rời nhóm <a id="uc07-moi-thanh-vien-roi-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Chủ nhóm mời một thành viên `ACTIVE` ra khỏi nhóm.
- **Tiền điều kiện:**
    - Không thể tự mời chính mình rời (`409 CANNOT_REMOVE_OWNER`).
    - Người bị mời rời **không được đang giữ quỹ** (`409 TREASURER_MUST_TRANSFER_FIRST` — chủ nhóm phải dùng UC06 chuyển
      quỹ trước).
    - Nếu nhóm **bật tính thừa thiếu**: Nhóm không còn giao dịch `PENDING` và phần của người đó (`net_balance`) phải
      bằng 0 ([rule.md](rule.md) quy tắc 27).
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}/members/{userId}`.
    2. Hệ thống kiểm tra đầy đủ các điều kiện tiền đề.
    3. Trong 1 transaction CSDL:
        - Cập nhật `group_members`: `status = 'REMOVED'`, `left_at = now()`.
        - Tự động chuyển toàn bộ giao dịch `PENDING` do người này tạo (`created_by`) sang `status = 'REJECTED'`.
    4. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `409 TREASURER_MUST_TRANSFER_FIRST`: Thành viên bị mời đang giữ quỹ.
    - `409 HAS_PENDING_TRANSACTIONS`: Nhóm còn khoản chờ duyệt (khi bật tính thừa thiếu).
    - `409 MEMBER_SHARE_NOT_ZERO`: Người bị mời còn nợ hoặc còn tiền trong quỹ (khi bật tính thừa thiếu).

---

#### UC08: Tự rời nhóm <a id="uc08-tu-roi-nhom"></a>

- **Tác nhân:** `Member`
- **Mô tả:** Thành viên chủ động rời khỏi nhóm chung quỹ.
- **Tiền điều kiện:**
    - Không phải là `OWNER` (`409 OWNER_MUST_TRANSFER_FIRST` — phải dùng UC05 chuyển quyền trước).
    - Không đang giữ quỹ (`409 TREASURER_MUST_TRANSFER_FIRST` — phải yêu cầu chủ nhóm bàn giao quỹ trước).
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
    - `409 OWNER_MUST_TRANSFER_FIRST`: Chủ nhóm cố rời nhóm mà chưa chuyển quyền.
    - `409 TREASURER_MUST_TRANSFER_FIRST`: Thủ quỹ cố rời nhóm mà chưa bàn giao quỹ.
    - `409 MEMBER_SHARE_NOT_ZERO`: Phần trong quỹ khác 0 (khi bật tính thừa thiếu).

---

#### UC09: Lưu trữ và mở lại nhóm <a id="uc09-luu-tru-mo-lai-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Đưa nhóm vào trạng thái lưu trữ (chỉ xem, đóng băng mọi thao tác ghi) hoặc mở lại hoạt động bình thường.
- **Luồng chính - Lưu trữ (Archive):**
    1. `Owner` gọi `POST /v1/groups/{id}/archive`.
    2. Kiểm tra nhóm đang `ACTIVE` và **không còn khoản PENDING** (`409 HAS_PENDING_TRANSACTIONS`).
    3. Cập nhật `groups.status = 'ARCHIVED'`. Trả về `200 OK`.
- **Luồng chính - Mở lại (Unarchive):**
    1. `Owner` gọi `POST /v1/groups/{id}/unarchive`.
    2. Cập nhật `groups.status = 'ACTIVE'`. Trả về `200 OK`.
- **Luồng ngoại lệ:**
    - `403 FORBIDDEN_OWNER_REQUIRED`: Người gọi không phải chủ nhóm.
    - `409 HAS_PENDING_TRANSACTIONS`: Còn khoản chờ duyệt khi lưu trữ.

---

#### UC10: Xoá nhóm <a id="uc10-xoa-nhom"></a>

- **Tác nhân:** `Owner`
- **Mô tả:** Xoá mềm nhóm và đóng quỹ nhóm vĩnh viễn.
- **Tiền điều kiện ([rule.md](rule.md) quy tắc 23):**
    - Nhóm không còn bất kỳ khoản giao dịch nào ở trạng thái `PENDING`.
    - Số dư quỹ `current_balance == 0`.
    - Nếu bật tính thừa thiếu: Phần trong quỹ của toàn bộ thành viên (kể cả cựu thành viên đã rời) đều phải bằng 0.
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}`.
    2. Hệ thống xác thực 3 điều kiện tiền đề trên.
    3. Trong 1 transaction CSDL:
        - Cập nhật `groups.status = 'DELETED'`.
        - Cập nhật `group_funds.status = 'CLOSED'`.
    4. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `409 HAS_PENDING_TRANSACTIONS`: Còn khoản chờ duyệt.
    - `409 CANNOT_DELETE_GROUP_WITH_BALANCE`: Quỹ còn tiền hoặc còn người có số dư thừa/thiếu.

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
        - Nếu danh sách người tham gia bao gồm tất cả thành viên có mặt lúc đó → hệ thống tự động lưu `participants: []`
          (rỗng = cả nhóm cùng chịu).
    3. Xác định trạng thái duyệt:
        - Nếu người ghi là `Treasurer` hoặc `Owner` → Gán `status = 'CONFIRMED'`, `reviewed_by = userId`,
          `reviewed_at = now()`. Nếu `money_source == 'FUND'`, khoá quỹ và trừ số dư
          `current_balance = current_balance - amount`.
        - Nếu người ghi là thành viên thường → Gán `status = 'PENDING'`, chưa tác động đến quỹ.
    4. Lưu bản ghi `group_transactions` và các dòng `group_transaction_participants`.
    5. Trả về `201 Created` kèm `GroupTransactionDetailRes`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 DATE_IN_FUTURE`: Thời điểm phát sinh ở tương lai.
    - `400 PAYER_NOT_MEMBER` / `PARTICIPANT_NOT_MEMBER`: Người trả hoặc người chia tiền không có mặt tại thời điểm giao
      dịch.
    - `400 SYSTEM_CATEGORY_REQUIRED`: Danh mục không phải danh mục hệ thống loại chi.
    - `400 PARTICIPANTS_SUM_MISMATCH`: Tổng tiền tự nhập không khớp với số tiền chi.
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
    4. Lưu bản ghi và trả về `201 Created`.
- **Luồng ngoại lệ (Exception Flows):**
    - `400 MONEY_SOURCE_INVALID`: Góp quỹ mà chọn nguồn tiền `FUND`.
    - `400 PARTICIPANTS_NOT_ALLOWED`: Góp quỹ có gắn người tham gia chia tiền.

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
        - **Khoá dòng quỹ (`FOR UPDATE`)** và cập nhật số dư quỹ tương ứng:
            - `EXPENSE (FUND)`: Giảm quỹ.
            - `CONTRIBUTION`: Tăng quỹ.
    3. Phản hồi `200 OK`.
- **Luồng chính - Từ chối đơn lẻ / Hàng loạt:**
    1. Tác nhân gọi `POST .../{tId}/reject` hoặc `POST .../transactions/bulk-reject`.
    2. Chuyển `status = 'REJECTED'`, gán thông tin người duyệt. Quỹ không bị tác động.
    3. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_TREASURER_REQUIRED`: Người duyệt không phải Thủ quỹ hoặc Chủ nhóm.
    - `409 TRANSACTION_NOT_PENDING`: Giao dịch đã được duyệt hoặc bị từ chối trước đó.

---

#### UC14: Chỉnh sửa giao dịch <a id="uc14-chinh-sua-giao-dich"></a>

- **Tác nhân:** `Member` (người tạo), `Owner`
- **Mô tả:** Sửa thông tin giao dịch (`EXPENSE`, `CONTRIBUTION`, `REFUND`) qua Strategy Pattern.
- **Quy tắc phân quyền:** Người tạo (`created_by`) được sửa khoản của mình; `Owner` được sửa mọi khoản.
- **Luồng chính (Success Flow):**
    1. Tác nhân gửi thông tin cập nhật (`PUT /v1/groups/{id}/transactions/{tId}`).
    2. Trong 1 transaction CSDL:
        - Nếu khoản đang `CONFIRMED`: Hoàn tác ảnh hưởng số tiền cũ lên quỹ.
        - Điều phối tới `GTransactionUpdateStrategy` phụ trách loại giao dịch đó để cập nhật thông tin và danh sách
          người tham gia (nếu có).
        - Kiểm tra người sửa:
            - Nếu là `Treasurer` hoặc `Owner` → Khoản giữ nguyên `CONFIRMED`, áp ảnh hưởng mới lên quỹ.
            - Nếu là `Member` thường → Khoản chuyển về `PENDING`, chờ duyệt lại.
            - Nếu khoản đang `REJECTED` được người tạo sửa lại → Tự động quay về `PENDING`.
        - Gửi sự kiện `FundBalanceChangedEvent` nếu có chênh lệch delta số dư quỹ.
    3. Phản hồi `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_TRANSACTION_EDIT`: Thành viên thường cố sửa giao dịch của người khác.
    - `400 TRANSACTION_TYPE_NOT_ALLOWED`: Cố ý đổi `type` sang loại khác hoặc sửa giao dịch kiểm kê (`ADJUSTMENT_*`).

---

#### UC15: Xoá giao dịch <a id="uc15-xoa-giao-dich"></a>

- **Tác nhân:** `Owner` (độc quyền)
- **Mô tả:** Xoá mềm một khoản giao dịch khỏi sổ nhóm ([rule.md](rule.md) quy tắc 21).
- **Luồng chính (Success Flow):**
    1. `Owner` gọi `DELETE /v1/groups/{id}/transactions/{tId}`.
    2. Trong 1 transaction CSDL:
        - Gán `deleted_at = now()` (xoá mềm).
        - Nếu khoản đang `CONFIRMED`: Khoá quỹ và hoàn tác toàn bộ ảnh hưởng tài chính của khoản đó lên
          `group_funds.current_balance`.
    3. Trả về `200 OK`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_OWNER_REQUIRED`: Thành viên thường hoặc thủ quỹ cố thực hiện xoá giao dịch.

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
    - Phần trong quỹ (`net_balance`) của người nhận giảm tương ứng.
- **Luồng chính (Success Flow):**
    1. Tác nhân gửi yêu cầu tạo giao dịch (`POST /v1/groups/{id}/transactions` với `type = 'REFUND'`,
       `money_source = 'FUND'`, `transactor_id` là người nhận).
    2. Hệ thống kiểm tra điều kiện giới hạn số tiền (`RefundTransactionStrategy`).
    3. Khoá dòng quỹ, trừ tiền quỹ, ghi nhận giao dịch `CONFIRMED`.
    4. Trả về `201 Created`.
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_TREASURER_REQUIRED`: Không có quyền thủ quỹ/chủ nhóm.
    - `400 CANNOT_REFUND_EXCEED_BALANCE`: Số tiền trả vượt quá số còn lại trong quỹ của người nhận.

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
        - Người chịu độ lệch: Mặc định chia đều cả nhóm (`participants: []`); nếu có `excluded_user_ids` thì ghi các
          thành viên còn lại vào `group_transaction_participants`.
        - Cập nhật số dư quỹ bằng câu lệnh cộng dồn nguyên tử: `SET current_balance = current_balance + :difference`
          (không ghi đè).
    3. Trả về `200 OK` kèm kết quả đối soát (`GroupFundReconcileRes`).
- **Luồng ngoại lệ (Exception Flows):**
    - `403 FORBIDDEN_TREASURER_REQUIRED`: Không có quyền thủ quỹ hoặc chủ nhóm.
    - `400 DATE_IN_FUTURE`: Thời điểm kiểm kê ở tương lai.

---

### PHẦN 3: BÁO CÁO & TRA CỨU <a id="phan-3-bao-cao-tra-cuu"></a>

#### UC19: Tra cứu thông tin nhóm & Quỹ <a id="uc19-tra-cuu-thong-tin-nhom"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Xem danh sách các nhóm đang tham gia, chi tiết cấu hình nhóm, danh sách thành viên và thông tin quỹ nhóm.
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `GET /v1/groups` để lấy tóm tắt các nhóm đang `ACTIVE` (hoặc `ARCHIVED`).
    2. Gọi `GET /v1/groups/{id}` để xem chi tiết nhóm, danh sách thành viên và vai trò.
    3. Gọi `GET /v1/groups/{id}/fund` để xem tên quỹ, người giữ quỹ và số dư hiện tại.
- **Luồng ngoại lệ:**
    - `403 FORBIDDEN_NOT_GROUP_MEMBER`: Người gọi không phải là thành viên `ACTIVE` của nhóm.

---

#### UC20: Tra cứu lịch sử giao dịch nhóm <a id="uc20-tra-cuu-lich-su-giao-dich"></a>

- **Tác nhân:** `Member`, `Treasurer`, `Owner`
- **Mô tả:** Tra cứu danh sách giao dịch có phân trang, lọc đa tiêu chí (theo nguồn tiền, loại, trạng thái duyệt, khoảng
  ngày, người chi).
- **Luồng chính (Success Flow):**
    1. Thành viên gọi `GET /v1/groups/{id}/transactions` kèm tham số lọc và phân trang (`page`, `size`).
    2. Hệ thống truy vấn các khoản chưa xoá (`deleted_at IS NULL`), trả về cả các khoản `PENDING` và `REJECTED` để các
       thành viên theo dõi tiến độ duyệt.
    3. Gọi `GET .../transactions/{tId}` để xem chi tiết danh sách người cùng chịu khoản chi và số tiền mỗi người chịu.

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

|  Mã UC   | Tên Use Case                           | Khách (`User`) | Thành viên (`Member`) | Thủ quỹ (`Treasurer`) | Chủ nhóm (`Owner`) | Nhóm lưu trữ (`ARCHIVED`) |
|:--------:|:---------------------------------------|:--------------:|:---------------------:|:---------------------:|:------------------:|:-------------------------:|
| **UC01** | Tạo nhóm mới                           |       ✅       |          ✅           |          ✅           |         ✅         |             —             |
| **UC02** | Tham gia nhóm bằng mã mời              |       ✅       |          ❌           |          ❌           |         ❌         |         ❌ (Chặn)         |
| **UC03** | Lấy / Làm mới mã mời                   |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC04** | Duyệt / Từ chối thành viên chờ         |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC05** | Chuyển quyền chủ nhóm                  |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC06** | Bàn giao thủ quỹ                       |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC07** | Mời thành viên rời nhóm                |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC08** | Tự rời nhóm                            |       ❌       |          ✅           |   ❌ (Phải chuyển)    |  ❌ (Phải chuyển)  |         ❌ (Chặn)         |
| **UC09** | Lưu trữ / Mở lại nhóm                  |       ❌       |          ❌           |          ❌           |         ✅         |        ✅ (Mở lại)        |
| **UC10** | Xoá nhóm                               |       ❌       |          ❌           |          ❌           |         ✅         |       ✅ (Cho phép)       |
| **UC11** | Ghi nhận chi tiêu (`EXPENSE`)          |       ❌       |     ✅ (PENDING)      |    ✅ (CONFIRMED)     |   ✅ (CONFIRMED)   |         ❌ (Chặn)         |
| **UC12** | Đóng góp tiền quỹ (`CONTRIBUTION`)     |       ❌       |     ✅ (PENDING)      |    ✅ (CONFIRMED)     |   ✅ (CONFIRMED)   |         ❌ (Chặn)         |
| **UC13** | Duyệt / Từ chối giao dịch              |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC14** | Sửa giao dịch                          |       ❌       |     ✅ (Của mình)     |     ✅ (Của mình)     |    ✅ (Tất cả)     |         ❌ (Chặn)         |
| **UC15** | Xoá giao dịch                          |       ❌       |          ❌           |          ❌           |         ✅         |         ❌ (Chặn)         |
| **UC16** | Quỹ trả tiền cho thành viên (`REFUND`) |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC18** | Kiểm kê quỹ thực tế (`Reconcile`)      |       ❌       |          ❌           |          ✅           |         ✅         |         ❌ (Chặn)         |
| **UC19** | Tra cứu thông tin nhóm & Quỹ           |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC20** | Tra cứu lịch sử giao dịch nhóm         |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC21** | Xem bảng phân bổ số dư & Cần nộp       |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
| **UC22** | Xem tổng quan tài chính nhóm           |       ❌       |          ✅           |          ✅           |         ✅         |       ✅ (Chỉ đọc)        |
