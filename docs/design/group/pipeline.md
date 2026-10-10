# LUỒNG XỬ LÝ & CÁCH TÍNH — PHÂN HỆ NHÓM CHUNG QUỸ

> **File này trả lời:** khi người dùng làm một việc, hệ thống **xử lý theo thứ tự nào** và **tính con số ra sao**.
> Phần nào liên quan module khác (danh mục, giao dịch cá nhân, thông báo) thì chỉ ghi **"bắn event"**, nhóm không biết
> bên kia làm gì.

## 📑 MỤC LỤC

- [1. Ai được chia mặc định](#1-nhung-ai-co-mat)
- [2. Xác định người tham gia và chia đều](#2-xac-dinh-nguoi-tham-gia)
- [3. Số dư quỹ](#3-so-du-quy)
- [4. Phần của mỗi người trong quỹ](#4-phan-cua-moi-nguoi)
- [5. Bảng tiền từng người và số cần nộp thêm](#5-so-tien-can-nop-them)
- [6. Tạo nhóm](#6-tao-nhom)
- [7. Ghi, xác nhận, sửa, xoá giao dịch](#7-ghi-khoan-chi)
- [8. Góp quỹ](#8-gop-quy)
- [9. Kiểm kê quỹ](#9-kiem-ke-quy)
- [10. Rời nhóm và mời rời nhóm](#10-roi-nhom)
- [11. Ví dụ kiểm chứng](#11-vi-du-kiem-chung)
- [12. Quỹ trả tiền cho thành viên (`REFUND`)](#12-tra-lai-tien)
- [13. Lưu trữ và mở lại nhóm](#13-luu-tru-nhom)
- [14. Xoá nhóm](#14-xoa-nhom)
- [15. Ghi sang sổ cá nhân và các sự kiện module nhóm bắn ra](#15-so-ca-nhan)
- [16. Vào nhóm và quản lý thành viên](#16-thanh-vien)

---

## 1. Ai được chia mặc định <a id="1-nhung-ai-co-mat"></a>

Dùng khi một khoản **không có danh sách người tham gia** (tức là của cả nhóm).

Hệ thống lấy mọi thành viên có `status = ACTIVE` **tại lúc ghi**:

    sql
    SELECT user_id
    FROM group_members
    WHERE group_id = :groupId
    AND status = 'ACTIVE';

- **Không** xét `occurred_at`. Ghi bù một khoản của ngày cũ vẫn chia cho những người đang `ACTIVE` hôm nay; người đã rời
  (`LEFT` / `REMOVED`) hay đang chờ duyệt vào nhóm (`PENDING`) không được chia.
- Người trả, người góp, người nhận hoàn tiền và mọi người tham gia gửi lên cũng phải đang `ACTIVE` tại lúc ghi
  (`GROUP_MEMBER_NOT_IN_GROUP`).
- Không cho ghi thời điểm ở tương lai (`GROUP_TXN_DATE_IN_FUTURE`).
- Khoản đã ghi **không tự đổi** khi người tham gia sau đó rời nhóm: dòng người tham gia đã lưu vẫn còn, phần của người
  đã rời vẫn nằm trong bảng tích luỹ (mục 4).

---

## 2. Xác định người tham gia và chia đều <a id="2-xac-dinh-nguoi-tham-gia"></a>

Chỉ khoản chi (`EXPENSE`) và kiểm kê (`ADJUSTMENT_*`) có người tham gia. `CONTRIBUTION` không được có
(`GROUP_TXN_PARTICIPANTS_NOT_ALLOWED`); `REFUND` không có.

```text
Không gửi danh sách người tham gia (hoặc rỗng) →  chia đều cho mọi thành viên ACTIVE (mục 1)
Có gửi danh sách người tham gia                →  mỗi dòng PHẢI có user_id và share_amount > 0
```

**Không có dòng nào được để trống `share_amount`.** Muốn chia đều thì **không gửi danh sách**; đã gửi danh sách thì phải
gửi đủ số tiền từng người.

**Khi gửi danh sách**, hệ thống kiểm tra theo thứ tự:

1. Mỗi dòng có `share_amount > 0` (`GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID`).
2. Không người nào xuất hiện hai lần (`GROUP_TXN_PARTICIPANT_DUPLICATED`).
3. Tổng các `share_amount` bằng đúng `amount` của giao dịch (`GROUP_TXN_PARTICIPANTS_SUM_MISMATCH`).
4. Mọi người trong danh sách, cùng người trả, đều là thành viên `ACTIVE` (`GROUP_MEMBER_NOT_IN_GROUP`).

**Khi không gửi danh sách**, hệ thống tự chia đều ngay lúc ghi:

- Phần cơ bản: `base_share = amount / số người`
- Phần dư: `remainder = amount % số người`
- Sắp xếp người tham gia theo `user_id` tăng dần (so sánh chuỗi UUID); `remainder` người đầu tiên được cộng thêm 1đ để
  tổng luôn bằng đúng `amount`.
  Ví dụ: 500.000đ chia 3 người → 166.667đ + 166.667đ + 166.666đ = 500.000đ.

Dù chia đều hay chia cụ thể, **toàn bộ dòng người tham gia đều được lưu vào `group_transaction_participants` với
`share_amount` rõ ràng > 0**.

---

## 3. Số dư quỹ <a id="3-so-du-quy"></a>

Mỗi nhóm có đúng một quỹ. Ảnh hưởng của từng khoản lên quỹ:

| `type`            | `money_source` |    Quỹ     |
|:------------------|:---------------|:----------:|
| `EXPENSE`         | `FUND`         | − `amount` |
| `EXPENSE`         | `PERSONAL`     | không đổi  |
| `CONTRIBUTION`    | `PERSONAL`     | + `amount` |
| `REFUND`          | `FUND`         | − `amount` |
| `ADJUSTMENT_UP`   | `FUND`         | + `amount` |
| `ADJUSTMENT_DOWN` | `FUND`         | − `amount` |

```text
current_balance = Σ ảnh hưởng của mọi khoản CONFIRMED, chưa xoá
```

- Chỉ tính khoản **`CONFIRMED` và chưa xoá**. Khoản `PENDING` và `REJECTED` không đụng quỹ.
- Quỹ được cập nhật **lúc khoản trở thành `CONFIRMED`**, và hoàn tác lúc khoản rời khỏi `CONFIRMED` hoặc bị xoá, trong
  cùng transaction. Cách làm: bắn event đổi số dư quỹ mang `delta`, nơi nhận chạy một câu cộng dồn nguyên tử, không đọc
  rồi ghi đè:

        sql
        UPDATE group_funds 
        SET current_balance = current_balance + :delta 
        WHERE group_id = :groupId;

  `delta = 0` thì không bắn.
- **Cơ chế khoá:**
    - **Khoá bi quan (`SELECT ... FOR UPDATE`) trên dòng quỹ** `group_funds`: khi kiểm kê (mục 9), khi đổi thủ quỹ (mục
        16) và khi thủ quỹ rời nhóm để bàn giao quỹ (mục 10). Việc cộng dồn `delta` ở trên đã nguyên tử nên không cần
            khoá
            thêm.
    - **Khoá bi quan trên dòng `group_member_balances` của người nhận** khi kiểm hạn mức `REFUND` (mục 12), để hai lần
      hoàn tiền cùng lúc không cùng vượt hạn mức.
    - **Khoá lạc quan (`@Version`)** trên `group_transactions`: hai người cùng duyệt hoặc sửa một khoản, người đến sau
      nhận lỗi xung đột.
- Quỹ **không có số dư ban đầu**, luôn bắt đầu từ 0. Nhờ vậy mọi đồng trong quỹ đều thuộc phần của một người (mục 4).

---

## 4. Phần của mỗi người trong quỹ <a id="4-phan-cua-moi-nguoi"></a>

Phần của mỗi người luôn được tính, dù bật hay tắt tính thừa thiếu; cờ chỉ quyết định ứng dụng có hiện phần quyết toán
hay không. Đây chính là số "còn bao nhiêu trong quỹ" (`net_balance`):

```text
net_balance = contribution + paid_out_of_pocket − refund − share

Trong đó:
- contribution       = Σ CONTRIBUTION (nộp quỹ)
- paid_out_of_pocket = Σ EXPENSE nguồn PERSONAL (chi tiền túi)
- refund             = Σ REFUND (quỹ hoàn trả tiền)
- share              = Σ phần chịu chi phí (từ group_transaction_participants; ADJUSTMENT_UP tính âm)

Bất biến trung tâm: Σ net_balance của mọi người = current_balance của quỹ
```

- Số dương = **quỹ đang giữ tiền của X** (thừa); số âm = **X cần góp thêm** (thiếu).

**Bảng tích luỹ `group_member_balances`.** Để đọc phần của một người trong $O (1)$ mà không quét lại lịch sử, 4 chỉ số
trên được lưu sẵn cho mỗi cặp `(group_id, user_id)` và **cộng dồn delta** mỗi khi có thay đổi:

1. Ảnh hưởng của một giao dịch lên 4 chỉ số chỉ tính khi giao dịch đang `CONFIRMED` và chưa xoá; ngược lại ảnh hưởng là
   rỗng.
2. Mỗi thao tác ghi, sửa, xoá, duyệt: chụp ảnh hưởng **trước**, tính ảnh hưởng **sau**, delta = sau − trước.
3. Delta bằng 0 thì không ghi. Ngược lại cộng dồn bằng `UPDATE ... SET col = col + :delta`, xử lý các người dùng theo
   thứ tự `user_id` tăng dần để tránh khoá chéo (deadlock).
4. Tuyệt đối không đọc dòng lên rồi gán giá trị mới, vì sẽ mất cập nhật khi hai giao dịch của cùng một người phát sinh
   đồng thời.

**"Phần trong quỹ" không phải là nợ giữa các thành viên.** Chênh lệch luôn giải quyết qua quỹ:

| Tình huống                        | Cách giải quyết                                                           |
|:----------------------------------|:--------------------------------------------------------------------------|
| Phần của C thấp (âm)              | C **góp thêm** (`CONTRIBUTION`)                                           |
| Phần của B cao vì đã trả tiền túi | Thủ quỹ **trả lại** B (`REFUND`, mục 12), hoặc để nguyên tới khi giải tán |
| Giải tán nhóm                     | Trả lại mỗi người đúng phần của họ (mục 14)                               |

---

## 5. Bảng tiền từng người và số cần nộp thêm <a id="5-so-tien-can-nop-them"></a>

**Bật tính thừa thiếu** — mỗi thành viên có: đã trả tiền túi, đã được hoàn, phải chịu, phần trong quỹ, cần nộp thêm.

**Tắt tính thừa thiếu** (không ai cần trả ai) — ứng dụng hiển thị: **mục tiêu**, **tổng tiền quỹ đang có**, và **số còn
lại của mỗi người trong quỹ** (`net_balance`). Các trường còn lại vẫn có trong response nhưng ứng dụng không hiển thị;
`total_needed_contribution` là `null`.

Không có số "đã góp" riêng: mỗi người chỉ có một con số là phần còn lại trong quỹ ở mục 4.

**Cần nộp thêm** (chỉ khi bật tính thừa thiếu). Hệ thống không gợi ý kiểu "X chuyển cho Y"; mọi chênh lệch giải quyết
qua quỹ:

1. **Mọi nhóm, có hay không có mục tiêu:** `target` chỉ để xem tiến độ gom quỹ, **không** chia đầu người.
    - Phần trong quỹ âm → cần nộp đúng bằng `|phần của X|`.
    - Phần không âm → `0`.
2. **Tổng cả nhóm** (`total_needed_contribution`) = cộng `needed_contribution` của mọi người.
3. **Người mới vào nhóm** có phần trong quỹ = 0, nên cần nộp = 0 cho tới khi bị chia một khoản chi.

**Hiển thị thành viên trong bảng tiền:**

- **Thành viên `ACTIVE`:** luôn hiển thị, sắp theo `user_id`.
- **Thành viên đã rời (`LEFT` / `REMOVED`):**
    - Phần trong quỹ bằng 0 → **ẩn hoàn toàn** cho bảng gọn.
    - Phần trong quỹ $\ne 0$ → **hiển thị** (kèm trạng thái đã rời) với số tiền thừa/thiếu, đặt sau các thành viên
      `ACTIVE`.
    - `cần nộp thêm`: phần âm thì `|phần trong quỹ|`, không âm thì `0`.

---

## 6. Tạo nhóm <a id="6-tao-nhom"></a>

**Yêu cầu:** tên (≤ 100 ký tự), mô tả (tuỳ chọn), `target` (tuỳ chọn, ≥ 0, không nhập thì `NULL`), cờ tính thừa thiếu,
cờ vào nhóm không cần duyệt, và **danh sách thành viên khác** (bắt buộc **ít nhất 1 người**, không được chứa chính người
tạo).

Trong **một** transaction CSDL:

1. Kiểm tra danh sách thành viên: chứa người tạo → `GROUP_OWNER_IN_MEMBER_LIST`; rỗng →
   `GROUP_MINIMUM_MEMBERS_REQUIRED`.
2. Tạo `groups`, sinh `invite_code` ngẫu nhiên 8 ký tự (chữ hoa và số, bỏ các ký tự dễ nhầm như `I`, `O`, `0`, `1`),
   không thời hạn.
3. Tạo **quỹ duy nhất** của nhóm trong `group_funds`: `keepper_id` = người tạo, số dư bắt đầu từ **0**. Bảng quỹ không
   có trường `status`.
4. Tạo `group_members` cho người tạo: `role = OWNER`, `status = ACTIVE`.
5. Tạo `group_members` cho mỗi thành viên khác (bỏ trùng): `role = MEMBER`, `status = ACTIVE` **ngay**, không qua bước
   chờ duyệt.

Mỗi nhóm luôn có đúng một quỹ, tạo cùng lúc với nhóm; thiếu bước 3 thì nhóm không hợp lệ.

Quỹ đã có sẵn tiền thì sau khi tạo nhóm, ghi mỗi người đã đưa tiền một khoản **góp quỹ** (mục 8). Không ghi thành số dư
ban đầu, vì số đó không thuộc phần của ai: tổng phần lệch số dư quỹ ngay từ đầu, và khi bật tính thừa thiếu thì không
trả lại được cho ai.

---

## 7. Ghi, xác nhận, sửa, xoá giao dịch <a id="7-ghi-khoan-chi"></a>

**Quyền hạn dùng ở các mục sau:**

- **Chủ nhóm** (`OWNER`) và **thủ quỹ** (người đang giữ quỹ, `keepper_id`) gọi chung là *người duyệt*.
- Người duyệt ghi khoản thì khoản được `CONFIRMED` ngay; thành viên thường ghi thì `PENDING` chờ duyệt.

### 7.1 Ghi

**Màn ghi khoản chi** chỉ hỏi thêm ba thứ, cả ba có sẵn giá trị mặc định:

```text
Trả bằng     [ Tiền quỹ ▾ ]         ← money_source: FUND (mặc định) | PERSONAL (tiền bản thân)
Ai tham gia    Tất cả (3)  ›        ← mặc định tất cả; chạm vào để bỏ tích
Lúc           [ Bây giờ ▾ ]         ← occurred_at, mặc định lúc mở màn; ghi bù thì chọn ngày và giờ
```

**Service**, trong một transaction CSDL:

1. Người gọi phải là thành viên `ACTIVE`; nhóm đang lưu trữ → chặn (`GROUP_ARCHIVED`); nhóm đã xoá → không tìm thấy.
2. Thời điểm không ở tương lai; người thực hiện (`transactor_id`) là thành viên `ACTIVE`.
3. Chọn cách xử lý theo `type`; loại không hỗ trợ → `GROUP_TXN_TYPE_NOT_ALLOWED`. Xác định trạng thái:

   | `type`                             | Người duyệt ghi                                               | Thành viên thường ghi                      |
                        |:-----------------------------------|:--------------------------------------------------------------|:-------------------------------------------|
   | `EXPENSE`, `CONTRIBUTION`          | `CONFIRMED`, `reviewed_by` = người ghi, `reviewed_at = now()` | `PENDING`                                  |
   | `REFUND`                           | `CONFIRMED`                                                   | **bị chặn** (`GROUP_TXN_TYPE_NOT_ALLOWED`) |
   | `ADJUSTMENT_UP`, `ADJUSTMENT_DOWN` | Chủ nhóm: `CONFIRMED`; thủ quỹ không phải chủ nhóm: `PENDING` | `PENDING`                                  |

4. Dựng giao dịch theo loại:

   | `type`         | Quy tắc                                                                                                                                                |
                        |:---------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
   | `EXPENSE`      | Bắt buộc có danh mục (`GROUP_TXN_CATEGORY_REQUIRED`), **bắn event** để kiểm tra danh mục, danh mục sai thì thao tác bị chặn. Người tham gia theo mục 2 |
   | `CONTRIBUTION` | `money_source` phải là `PERSONAL` (`GROUP_TXN_MONEY_SOURCE_INVALID`), không danh mục, không người tham gia                                             |
   | `REFUND`       | `money_source` phải là `FUND`, không danh mục, không người tham gia, `amount` không vượt phần hiện có của người nhận (mục 12)                          |
   | `ADJUSTMENT_*` | `money_source` phải là `FUND`, không danh mục, người tham gia theo mục 2                                                                               |

5. Quyết định ghi sang sổ cá nhân (mục 15): người gọi chính là người thực hiện thì **bắn event** ghi sổ cá nhân; ghi hộ
   hoặc `REFUND` thì gửi thông báo xin người thực hiện xác nhận. Kết quả lưu vào `personal_sync_status`.
6. Ghi một dòng `group_transactions` (kèm `personal_sync_status`) và các dòng `group_transaction_participants` (luôn có
   `share_amount` rõ ràng > 0); cập nhật bộ đếm khoản chờ duyệt.
7. Cập nhật delta vào bảng tích luỹ `group_member_balances` (mục 4).
8. Chỉ khi `CONFIRMED` → **bắn event** đổi số dư quỹ với `delta` theo bảng mục 3.

### 7.2 Xác nhận hoặc từ chối

Chỉ **người duyệt**; nhóm đang lưu trữ bị chặn; khoản phải đang `PENDING` (`GROUP_TXN_NOT_PENDING`). Trong một
transaction CSDL:

| Thao tác              | Endpoint                                | Kết quả                                                                                                                                                                                                                                              |
|:----------------------|:----------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Xác nhận đơn lẻ**   | `POST .../transactions/{txnId}/confirm` | `status = CONFIRMED`, ghi `reviewed_by`, `reviewed_at`. Cập nhật bộ đếm, cộng delta vào `group_member_balances`, **bắn event** đổi số dư quỹ                                                                                                         |
| **Từ chối đơn lẻ**    | `POST .../transactions/{txnId}/reject`  | `status = REJECTED`, ghi `reviewed_by`, `reviewed_at`. Không đụng tới quỹ và số dư thành viên. Khoản đã ghi sang sổ cá nhân thì **bắn event** xoá khoản đó (mục 15)                                                                                  |
| **Duyệt hàng loạt**   | `POST .../transactions/bulk-confirm`    | Danh sách id (bỏ trùng) phải **đều** là khoản `PENDING` của nhóm, sai một id thì cả lô bị từ chối (`VALIDATION_ERROR`). Chuyển tất cả sang `CONFIRMED`, gộp delta thành viên và gộp delta quỹ, cập nhật **một lần**, bắn **một** event đổi số dư quỹ |
| **Từ chối hàng loạt** | `POST .../transactions/bulk-reject`     | Cùng điều kiện danh sách như trên. Chuyển tất cả sang `REJECTED`, ghi người từ chối và thời điểm. Mỗi khoản đã ghi sang sổ cá nhân thì **bắn event** xoá (mục 15)                                                                                    |

### 7.3 Sửa

**Ai được sửa:**

- Người duyệt sửa được mọi khoản, trừ `ADJUSTMENT_*` (`GROUP_TXN_ADJUSTMENT_NOT_EDITABLE`).
- Thành viên thường chỉ sửa khoản **do chính mình tạo**, đang **không** `CONFIRMED`, và chỉ loại `EXPENSE` /
  `CONTRIBUTION`.

Thời điểm mới không ở tương lai; nhóm đang lưu trữ bị chặn. Trong một transaction CSDL:

1. Chụp trạng thái cũ: ảnh hưởng lên bảng tích luỹ (`before`), trạng thái ghi sổ cá nhân, và nếu khoản đang `CONFIRMED`
   thì tính delta **hoàn tác** lên quỹ (âm ảnh hưởng cũ).
2. Cập nhật các trường chung và trạng thái: người sửa là **người duyệt** → `CONFIRMED`, ghi `reviewed_by`,
   `reviewed_at`; người khác → `PENDING`, xoá `reviewed_by` / `reviewed_at`. Khoản `REJECTED` được sửa cũng về `PENDING`
   (thành viên thường) hoặc `CONFIRMED` (người duyệt). Chỉ trường nào client gửi lên mới đổi.
3. Cập nhật theo loại:
    - `EXPENSE`: **đổi số tiền thì bắt buộc gửi lại danh sách người tham gia** (`GROUP_TXN_PARTICIPANTS_REQUIRED`). Đổi
      danh mục thì **bắn event** kiểm tra danh mục. Có gửi danh sách thì kiểm tra và tính lại theo mục 2. Có thể đổi
      người trả và `money_source`.
    - `CONTRIBUTION`: ép `money_source = PERSONAL`, đổi được người góp, xoá danh sách người tham gia.
    - `REFUND`: xem mục 12.
4. Đối chiếu trạng thái ghi sổ cá nhân trước và sau (mục 15): **bắn event** ghi đè hoặc xoá khoản đã ghi, hoặc ghi mới
   nếu khoản vừa trở thành tiền túi.
5. Cộng chênh lệch giữa ảnh hưởng `before` và ảnh hưởng mới vào `group_member_balances`.
6. Quỹ: delta = hoàn tác ảnh hưởng cũ (nếu trước đó `CONFIRMED`) + áp ảnh hưởng mới (nếu giờ `CONFIRMED`). Khác 0 thì
   **bắn event** đổi số dư quỹ.

### 7.4 Xoá

**Ai được xoá:**

- **Chủ nhóm:** xoá được mọi khoản.
- **Thủ quỹ (không phải chủ nhóm):** không được xoá (`GROUP_TXN_DELETE_FORBIDDEN`).
- **Thành viên thường:** chỉ xoá khoản **do chính mình tạo** và đang **`PENDING`**.

Trong một transaction CSDL: chụp ảnh hưởng cũ `before`; khoản đang `CONFIRMED` thì **bắn event** đổi số dư quỹ với delta
hoàn tác; khoản đã ghi sang sổ cá nhân thì **bắn event** xoá (mục 15); đặt `deleted_at = now()`; cập nhật bộ đếm; hoàn
tác ảnh hưởng trong `group_member_balances`.

---

## 8. Góp quỹ <a id="8-gop-quy"></a>

B góp 1.000.000đ: ghi **một** dòng `CONTRIBUTION`, `money_source = PERSONAL`, `transactor_id = B`. B phải đang là thành
viên `ACTIVE`.

- B tự ghi → `PENDING`; người duyệt xác nhận khi đã **thật sự nhận tiền** → `CONFIRMED`, lúc đó quỹ mới `+ amount`.
- Người duyệt ghi hộ B → `CONFIRMED` ngay, cộng quỹ ngay.

Khoản góp còn được ghi sang sổ cá nhân của B (mục 15):

- B tự ghi → **bắn event** ghi sổ cá nhân ngay, dù khoản nhóm còn `PENDING`. Nếu khoản bị từ chối hoặc bị xoá thì **bắn
  event** xoá.
- Người duyệt ghi hộ B → chưa bắn event; gửi thông báo xin B xác nhận, B xác nhận thì mới **bắn event** ghi.
- Sửa khoản đã ghi sang sổ cá nhân → **bắn event** ghi đè.

Người đã rời nhóm (`LEFT` / `REMOVED`) **không** thể là người góp, vì người thực hiện phải đang `ACTIVE` (mục 1).

---

## 9. Kiểm kê quỹ <a id="9-kiem-ke-quy"></a>

Chủ nhóm hoặc thủ quỹ đếm tiền thật, bấm **"Kiểm kê"**, nhập số thực tế (`actual_balance`), có thể kèm lý do (≤ 255 ký
tự) và danh sách người **loại khỏi** phần chia (`excluded_user_ids`). Thời điểm kiểm kê là lúc bấm.

Trong **một** transaction CSDL:

1. **Khoá dòng quỹ** rồi đọc số dư mới nhất:

   ```sql
   SELECT * FROM group_funds
    WHERE group_id = :groupId
    FOR UPDATE;
   ```

   Khoản xác nhận hoặc ghi phát sinh cùng lúc phải chờ kiểm kê xong.
2. `actual_balance` bằng đúng số dư hiện tại → **báo lỗi** (`VALIDATION_ERROR`), không sinh gì.
3. Người bấm phải là chủ nhóm hoặc thủ quỹ hiện tại (`GROUP_TREASURER_REQUIRED`).
4. Nhóm **không được còn khoản `PENDING`** (`GROUP_PENDING_TXN_EXIST`), vì khoản chờ có thể đổi số dư ngay sau khi đếm.
5. `difference = actual_balance − current_balance`, `amount = |difference|`:

   | Kết quả | Hệ thống sinh |
                        | :------ | :------------ |
   | `difference < 0` | `ADJUSTMENT_DOWN`, `amount = |difference|` |
   | `difference > 0` | `ADJUSTMENT_UP`, `amount = difference` |

6. Người tham gia kiểm kê:
    - `excluded_user_ids` rỗng → chia đều cho **mọi thành viên `ACTIVE`**.
    - Có `excluded_user_ids` → chia đều (theo mục 2) cho mọi thành viên `ACTIVE` **trừ** những người bị loại.
7. Ghi giao dịch bằng đúng luồng ghi ở mục 7.1: `money_source = FUND`, `transactor_id` = thủ quỹ, `created_by` = người
   bấm, `occurred_at = now()`, ghi chú = lý do. Trạng thái theo bảng mục 7.1: **chủ nhóm bấm → `CONFIRMED` ngay**; thủ
   quỹ không phải chủ nhóm bấm → `PENDING` cho tới khi được xác nhận.
8. Khi `CONFIRMED`, **bắn event** đổi số dư quỹ với `delta = difference` (cộng chênh lệch, không ghi đè).

Nhờ bước 1, ngay sau khi kiểm kê (đã `CONFIRMED`) số dư bằng đúng số vừa đếm; nhờ bước 8, sổ luôn khớp số dư.

---

## 10. Rời nhóm và mời rời nhóm <a id="10-roi-nhom"></a>

**Tự rời** — thành viên `ACTIVE`, trong **một** transaction CSDL:

1. Chủ nhóm không rời được (`GROUP_OWNER_TRANSFER_REQUIRED`): phải **chuyển quyền chủ nhóm trước** (mục 16).
2. **Bắn event** thành viên rời nhóm. Nếu người đó đang giữ quỹ, quỹ **tự bàn giao cho chủ nhóm** (đổi `keepper_id`).
3. Đặt `status = LEFT`, `left_at = now()`.

**Chủ nhóm mời rời** (`DELETE .../members/{memberUserId}`):

1. Không tự mời mình (`VALIDATION_ERROR`); đối tượng phải đang `ACTIVE` (`GROUP_MEMBER_NOT_FOUND`).
2. **Bắn event** thành viên rời nhóm: người đó đang giữ quỹ thì quỹ bàn giao cho chủ nhóm.
3. Đặt `status = REMOVED`, `left_at = now()`.

**Không có điều kiện tất toán.** Hệ thống **không** kiểm tra phần của người rời có bằng 0 hay không, cũng **không** từ
chối giúp các khoản `PENDING` của họ: các khoản đã ghi giữ nguyên, khoản `PENDING` của người rời vẫn chờ người duyệt xác
nhận hoặc từ chối như bình thường.

**Hệ quả với phần còn lại của người đã rời:**

- Bảng tiền (mục 5) tiếp tục hiển thị người đó kèm số thừa/thiếu khi phần $\ne 0$; phần bằng 0 thì ẩn.
- Người đã rời **không thể là người góp hay người nhận `REFUND`** (cả hai đòi thành viên `ACTIVE`), nên nhóm không ghi
  bù hay trả ra cho họ qua hai loại này.
- Cách duy nhất để đưa phần của người đã rời về 0 là **sửa khoản cũ** và bỏ người đó khỏi danh sách người tham gia (mục
  7.3): khoản đó chỉ còn chia cho những người `ACTIVE`, phần của người đã rời giảm tương ứng.

**Ví dụ.** A, B, C mỗi người góp 1.000.000đ; B và C đi chơi riêng tiêu 2.400.000đ tiền quỹ, **bỏ tích A**. Phần: A
1.000.000 · B −200.000 · C −200.000; quỹ 600.000. C rời nhóm → hệ thống cho rời ngay; bảng tiền vẫn hiện C với phần
−200.000 và cần nộp 200.000 cho tới khi nhóm xử lý bằng cách sửa khoản chi bỏ C khỏi người tham gia.

---

## 11. Ví dụ kiểm chứng <a id="11-vi-du-kiem-chung"></a>

Nhóm A, B, C bật tính thừa thiếu, mỗi người góp 1.000.000đ, A giữ quỹ. Mọi khoản dưới đây đều đã `CONFIRMED`. B và C đi
chơi riêng hết 600.000đ, **bỏ tích A** — khoản này có 2 dòng người tham gia (B, C), mỗi người chịu
`share_amount = 300.000đ`.

**Trường hợp 1 — tiêu bằng tiền quỹ (`FUND`):**

|          | Đã nộp vào quỹ | Trả tiền túi | Phần phải chịu | **Phần trong quỹ** |
|:---------|---------------:|-------------:|---------------:|-------------------:|
| A        |      1.000.000 |            0 |              0 |      **1.000.000** |
| B        |      1.000.000 |            0 |        300.000 |        **700.000** |
| C        |      1.000.000 |            0 |        300.000 |        **700.000** |
| **Tổng** |                |              |                |      **2.400.000** |

Quỹ: `3.000.000 − 600.000 = 2.400.000` ✅

**Trường hợp 2 — B trả bằng tiền túi (`PERSONAL`):**

|          | Đã nộp vào quỹ | Trả tiền túi | Phần phải chịu | **Phần trong quỹ** |
|:---------|---------------:|-------------:|---------------:|-------------------:|
| A        |      1.000.000 |            0 |              0 |      **1.000.000** |
| B        |      1.000.000 |      600.000 |        300.000 |      **1.300.000** |
| C        |      1.000.000 |            0 |        300.000 |        **700.000** |
| **Tổng** |                |              |                |      **3.000.000** |

Quỹ vẫn `3.000.000` ✅. **A không bị ảnh hưởng ở cả hai trường hợp.**

**Tiếp trường hợp 2 — A trả lại B 600.000đ (`REFUND`):** quỹ `3.000.000 − 600.000 = 2.400.000`; phần của B
`1.300.000 − 600.000 = 700.000`. Kết quả **y hệt trường hợp 1** ✅.

**Kiểm kê tiếp theo trường hợp 1:** sổ báo 2.400.000đ, A đếm được 2.300.000đ.

| type              | money_source | transactor_id | created_by | category_id |  amount | note                                     |
|:------------------|:-------------|:--------------|:-----------|:------------|--------:|:-----------------------------------------|
| `ADJUSTMENT_DOWN` | `FUND`       | A             | A          | _NULL_      | 100.000 | Kiểm kê: sổ 2.400.000, thực tế 2.300.000 |

Hệ thống tự sinh 3 dòng người tham gia cho A, B, C (sắp theo `user_id` tăng dần rồi chia đều theo mục 2: A 33.334đ · B
33.333đ · C 33.333đ). Phần mới: **966.666 · 666.667 · 666.667**, tổng **2.300.000** ✅.

---

## 12. Quỹ trả tiền cho thành viên (`REFUND`) <a id="12-tra-lai-tien"></a>

Một loại giao dịch duy nhất cho mọi trường hợp tiền quỹ đi ra cho **một thành viên**, do người duyệt ghi: hoàn tiền túi
người đó đã trả hộ nhóm, hoặc trả lại tiền người đó đã góp (góp dư, giải tán). Quỹ giảm và phần trong quỹ của người nhận
giảm tương ứng.

Tất toán một người có phần dương: ghi `REFUND` đúng bằng phần của người đó.

**Sổ cá nhân của người nhận (mục 15):** `REFUND` do người duyệt ghi nên người nhận phải xác nhận qua thông báo thì mới
**bắn event** ghi vào sổ cá nhân của họ. Người ghi chính là người nhận thì **bắn event** ngay.

**Ghi** — trong **một** transaction CSDL:

1. Người ghi phải là thủ quỹ hoặc chủ nhóm (thành viên thường bị chặn); nhóm không đang lưu trữ.
2. Người nhận là thành viên `ACTIVE`; thời điểm không ở tương lai.
3. **Khoá dòng `group_member_balances` của người nhận**, rồi kiểm `amount` không vượt phần hiện có (`net_balance`) của
   người đó, dù bật hay tắt tính thừa thiếu (`GROUP_TXN_REFUND_EXCEEDS_BALANCE`).
4. Ghi một dòng `REFUND`: `money_source = FUND` (nguồn khác bị từ chối), `transactor_id` = người nhận, `created_by` =
   người ghi, **`status = CONFIRMED`**, không danh mục, không người tham gia.
5. Quyết định ghi sổ cá nhân (mục 15), cộng delta vào `group_member_balances` (tăng `refund` của người nhận), **bắn
   event** đổi số dư quỹ `− amount`.

**Sửa** — chỉ thủ quỹ hoặc chủ nhóm, không đổi loại, trong **một** transaction CSDL:

1. Người nhận mới (nếu đổi) phải là thành viên `ACTIVE`.
2. Kiểm hạn mức **chỉ khi** số tiền **tăng** hoặc **đổi người nhận** (giảm hoặc giữ nguyên thì luôn cho qua). Khoá dòng
   `group_member_balances` của cả người nhận cũ lẫn mới, rồi tính **không kể chính khoản đang sửa**:
    - Giữ người nhận → `amount mới ≤ số hiện tại + amount cũ`.
    - Đổi người nhận → `amount mới ≤ số hiện tại của người mới`.
3. Ghi giá trị mới (người nhận, số tiền, thời điểm, ghi chú); `reviewed_by` = người sửa, `reviewed_at = now()`. Khoản
   **vẫn `CONFIRMED`**.
4. Quỹ: hoàn tác khoản cũ (`+ amount cũ`) rồi áp khoản mới (`− amount mới`), bắn event đổi số dư quỹ nếu tổng delta khác
    0.
5. Sổ cá nhân (mục 15): khoản đã ghi sang sổ cá nhân của người nhận → **bắn event** ghi đè; đổi người nhận thì **bắn
   event** xoá khoản của người cũ rồi xử lý như ghi mới cho người mới.

---

## 13. Lưu trữ và mở lại nhóm <a id="13-luu-tru-nhom"></a>

**Lưu trữ** — chỉ chủ nhóm:

1. Nhóm đang `ACTIVE`, **không còn khoản `PENDING`** (`GROUP_PENDING_TXN_EXIST`) → nếu còn thì chặn, yêu cầu xác nhận
   hoặc từ chối hết trước.
2. `groups.status = ARCHIVED`.

**Khi đang lưu trữ:** thành viên chỉ được xem; mọi thao tác ghi bị chặn (`GROUP_ARCHIVED`). Các việc vẫn làm được: chủ
nhóm **mở lại** hoặc **xoá nhóm**, và người thực hiện vẫn **xác nhận ghi sổ cá nhân** (mục 15) vì tiền đã rời túi họ từ
trước.

**Mở lại** — chỉ chủ nhóm, nhóm phải đang `ARCHIVED` (`GROUP_NOT_ARCHIVED`): `groups.status = ACTIVE`.

---

## 14. Xoá nhóm <a id="14-xoa-nhom"></a>

Chỉ chủ nhóm, nhóm đang `ACTIVE` hoặc `ARCHIVED`, trong **một** transaction CSDL:

1. Số dư quỹ phải bằng 0 (`GROUP_DELETE_BALANCE_NOT_ZERO`).
2. Không còn khoản `PENDING` (`GROUP_PENDING_TXN_EXIST`).
3. `groups.status = DELETED`.

Hệ thống **không** kiểm tra phần trong quỹ của từng thành viên. Σ phần của mọi người luôn bằng số dư quỹ nên quỹ bằng 0
chưa chắc từng người bằng 0; nhóm nên tự tất toán trước khi xoá: ghi `REFUND` (mục 12) cho người còn phần dương, người
còn phần âm **góp thêm** (mục 8).

**Giải tán điển hình** (bật tính thừa thiếu): quỹ còn 2.400.000đ, phần A 1.000.000 · B 700.000 · C 700.000 → ghi 3 khoản
`REFUND` đúng bằng phần mỗi người → quỹ 0, phần mọi người 0 → xoá được.

---

## 15. Ghi sang sổ cá nhân và các sự kiện module nhóm bắn ra <a id="15-so-ca-nhan"></a>

Module nhóm chỉ lo **ai cần ghi, khi nào, trạng thái ra sao**. Việc ghi, sửa, xoá khoản ở sổ cá nhân do module khác làm
khi nhận event; nhóm không biết chi tiết bên đó.

**Khoản nào ghi sang sổ cá nhân** — theo `type` và `money_source`:

| `type`                              | `money_source` | Ghi sang sổ cá nhân của `transactor_id`? |
|:------------------------------------|:---------------|:-----------------------------------------|
| `CONTRIBUTION`                      | `PERSONAL`     | Có                                       |
| `EXPENSE`                           | `PERSONAL`     | Có (người đó đã bỏ tiền túi)             |
| `REFUND`                            | `FUND`         | Có (người đó nhận tiền)                  |
| `EXPENSE`                           | `FUND`         | Không                                    |
| `ADJUSTMENT_UP` / `ADJUSTMENT_DOWN` | `FUND`         | Không                                    |

**Trạng thái `personal_sync_status` của giao dịch nhóm:**

| Giá trị            | Ý nghĩa                                        |
|:-------------------|:-----------------------------------------------|
| `NONE`             | Không áp dụng, hoặc khoản đã bị xoá/từ chối    |
| `AWAITING_CONFIRM` | Đã gửi thông báo, chờ `transactor_id` xác nhận |
| `SYNCED`           | Đã bắn event ghi sang sổ cá nhân               |
| `DECLINED`         | `transactor_id` từ chối ghi                    |

**Tạo:**

| Người gọi                                                        | Kết quả            | Nhóm làm gì                                                |
|:-----------------------------------------------------------------|:-------------------|:-----------------------------------------------------------|
| = `transactor_id`                                                | `SYNCED`           | **Bắn event** ghi sổ cá nhân                               |
| ≠ `transactor_id` (ghi hộ, hoặc mọi `REFUND` do người duyệt ghi) | `AWAITING_CONFIRM` | Gửi thông báo xin `transactor_id` xác nhận, chưa bắn event |
| Khoản không thuộc bảng trên                                      | `NONE`             | Không làm gì                                               |

**Xác nhận** — `POST .../transactions/{txnId}/personal-sync` với `accept`:

1. Chỉ chính `transactor_id` được gọi; người khác nhận `404 GROUP_TRANSACTION_NOT_FOUND`. Nhóm đã lưu trữ vẫn xác nhận
   được.
2. Khoản phải đang `AWAITING_CONFIRM`, nếu không → `409 GROUP_TXN_PERSONAL_SYNC_NOT_AWAITING`.
3. `accept = true` → **bắn event** ghi sổ cá nhân, `SYNCED`. `accept = false` → `DECLINED`, không bắn gì.

**Sửa** — so trạng thái trước và sau:

| Trước                           | Sau khi sửa                                     | Nhóm làm gì                                                         | Trạng thái mới |
|:--------------------------------|:------------------------------------------------|:--------------------------------------------------------------------|:---------------|
| `SYNCED`                        | Vẫn là tiền túi, cùng `transactor_id`           | **Bắn event** ghi đè                                                | `SYNCED`       |
| `SYNCED`                        | Không còn là tiền túi (vd. `PERSONAL` → `FUND`) | **Bắn event** xoá                                                   | `NONE`         |
| `SYNCED`                        | Đổi `transactor_id`                             | **Bắn event** xoá cho người cũ, rồi xử lý như tạo mới cho người mới | theo bảng Tạo  |
| `NONE`                          | Trở thành tiền túi                              | Xử lý như tạo mới                                                   | theo bảng Tạo  |
| `AWAITING_CONFIRM` / `DECLINED` | Cùng `transactor_id`                            | Không làm gì (thông báo cũ là lịch sử, không hỏi lại)               | giữ nguyên     |
| `AWAITING_CONFIRM` / `DECLINED` | Đổi `transactor_id`                             | Xử lý như tạo mới (gửi thông báo cho người mới)                     | theo bảng Tạo  |
| mọi trạng thái                  | Không còn là tiền túi                           | Không làm gì                                                        | `NONE`         |

**Xoá hoặc từ chối:** khoản đang `SYNCED` thì **bắn event** xoá; mọi trường hợp đều về `NONE`.

**Các sự kiện module nhóm bắn ra:**

| Event                  | Bắn khi                                           | Bên nhận làm                                                |
|:-----------------------|:--------------------------------------------------|:------------------------------------------------------------|
| Ghi sổ cá nhân         | Tự ghi, xác nhận, sửa khoản đã ghi                | Module giao dịch ghi hoặc ghi đè khoản ở sổ cá nhân         |
| Xoá khoản ở sổ cá nhân | Xoá, từ chối, hoặc khoản không còn là tiền túi    | Module giao dịch xoá khoản đã ghi                           |
| Kiểm tra danh mục      | Ghi hoặc sửa khoản chi có danh mục                | Module danh mục báo lỗi nếu danh mục không hợp lệ           |
| Đổi số dư quỹ          | Khoản `CONFIRMED` phát sinh, đổi hoặc bị hoàn tác | Nội bộ nhóm cộng dồn vào `group_funds`                      |
| Thành viên rời nhóm    | Thành viên rời hoặc bị mời rời                    | Nội bộ nhóm bàn giao quỹ cho chủ nhóm nếu người đó đang giữ |

- Các event chạy **đồng bộ trong cùng transaction** với thao tác nhóm: bên nhận báo lỗi thì cả thao tác nhóm bị huỷ,
  không để hai sổ lệch nhau.
- Thông báo xin xác nhận gửi sang module thông báo, hiển thị thế nào do module đó lo.

---

## 16. Vào nhóm và quản lý thành viên <a id="16-thanh-vien"></a>

**Vào bằng mã mời** (`POST /groups/join`): nhóm không bị xoá (`GROUP_NOT_FOUND`) và không đang lưu trữ
(`GROUP_ARCHIVED`); người gọi chưa là thành viên `ACTIVE` hoặc `PENDING`. Tạo `group_members` với `role = MEMBER`;
`status = ACTIVE` nếu nhóm bật "vào nhóm không cần duyệt", ngược lại `PENDING`.

**Chủ nhóm duyệt người xin vào:**

| Thao tác                   | Kết quả                                                               |
|:---------------------------|:----------------------------------------------------------------------|
| Duyệt một người            | `PENDING → ACTIVE`, `joined_at = now()`                               |
| Duyệt tất cả               | Như trên cho mọi người đang `PENDING`, trả số người đã duyệt          |
| Từ chối một người / tất cả | **Xoá** dòng `PENDING` (không để lại `LEFT`), trả số người đã từ chối |

**Chủ nhóm thêm trực tiếp:** người được thêm vào `ACTIVE` ngay (`joined_at = now()`); người đã `ACTIVE` hoặc đang
`PENDING` thì báo lỗi.

**Chuyển quyền chủ nhóm** (`PUT .../owner-role/{memberUserId}/`): không chuyển cho chính mình
(`GROUP_TRANSFER_TO_SELF`); người nhận phải đang `ACTIVE`. Hoán đổi vai trò trong **một** câu lệnh: người nhận thành
`OWNER`, chủ cũ thành `MEMBER`.

**Đổi thủ quỹ** (`PUT .../fund-kepper`, chỉ chủ nhóm): khoá dòng quỹ; thủ quỹ mới phải là thành viên `ACTIVE`
(`GROUP_FUND_HOLDER_NOT_MEMBER`); đổi `keepper_id`.

**Rời nhóm / mời rời nhóm:** xem mục 10.
