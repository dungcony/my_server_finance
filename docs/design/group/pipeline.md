# LUỒNG XỬ LÝ & CÁCH TÍNH — PHÂN HỆ NHÓM CHUNG QUỸ

> **File này trả lời:** khi người dùng làm một việc, hệ thống **xử lý theo thứ tự nào** và **tính con số ra sao**.
> Kịch bản Use Case → [use-case.md](use-case.md) · Luật phải tuân → [rule.md](rule.md) · Cấu trúc bảng → [lop-thuc-the.md](lop-thuc-the.md) · Endpoint → [api.md](api.md)

## 📑 MỤC LỤC

- [1. Những ai có mặt tại một thời điểm](#1-nhung-ai-co-mat)
- [2. Xác định người tham gia và chia đều](#2-xac-dinh-nguoi-tham-gia)
- [3. Số dư quỹ](#3-so-du-quy)
- [4. Phần của mỗi người trong quỹ](#4-phan-cua-moi-nguoi)
- [5. Bảng tiền từng người và số cần nộp thêm](#5-so-tien-can-nop-them)
- [6. Tạo nhóm](#6-tao-nhom)
- [7. Ghi, xác nhận, sửa, xoá giao dịch](#7-ghi-khoan-chi)
- [8. Góp quỹ](#8-gop-quy)
- [9. Kiểm kê quỹ](#9-kiem-ke-quy)
- [10. Rời nhóm](#10-roi-nhom)
- [11. Ví dụ kiểm chứng](#11-vi-du-kiem-chung)
- [12. Quỹ trả tiền cho thành viên (`REFUND`)](#12-tra-lai-tien)
- [13. Lưu trữ và mở lại nhóm](#13-luu-tru-nhom)
- [14. Xoá nhóm](#14-xoa-nhom)

---

## 1. Những ai có mặt tại một thời điểm <a id="1-nhung-ai-co-mat"></a>

Dùng khi một khoản **không có dòng người tham gia** (tức là của cả nhóm), và khi kiểm tra người trả / người tham gia / người nhận có hợp lệ không.

Liệt kê **đích danh** các trạng thái đã từng ở nhóm, không dùng điều kiện loại trừ, để thêm trạng thái mới sau này không lọt vào nhầm:

```sql
SELECT DISTINCT user_id
  FROM group_members
 WHERE group_id = :groupId
   AND status IN ('ACTIVE', 'LEFT', 'REMOVED')
   AND joined_at <= :occurredAt
   AND (left_at IS NULL OR left_at > :occurredAt)
 ORDER BY user_id;
```

- So sánh **thời điểm với thời điểm**, tới giây: `joined_at`, `left_at` và `occurred_at` đều là `TIMESTAMPTZ`, không cần đổi múi giờ.
- Có mặt từ **đúng lúc vào**, tới **ngay trước lúc rời**. Ví dụ C rời lúc 20:00 → bữa trưa 12:00 cùng ngày vẫn chia cho C, bữa tối 21:00 thì không.
- Không cho ghi thời điểm ở tương lai, nên lúc một người rời, mọi khoản đã ghi đều ở trước lúc rời. Phần của họ đã kiểm tra lúc rời (mục 10) không tự đổi.
- Không có ai → **không** lấy thành viên hiện tại thay vào. Kiểm tra lúc ghi đã chặn trường hợp này.

---

## 2. Xác định người tham gia và chia đều <a id="2-xac-dinh-nguoi-tham-gia"></a>

```text
Có dòng trong group_transaction_participants  →  đúng những người đó
Không có dòng nào                              →  mọi thành viên có mặt tại thời điểm giao dịch (mục 1)
```

**Chia đều** (khi `share_amount` trống):

```text
phần cơ bản = amount / số người
phần dư     = amount % số người    → thêm 1đ cho những người đầu danh sách

500.000 chia 3 → 166.667 + 166.667 + 166.666 = 500.000
```

Phần chia **tính lúc đọc**, nên danh sách người **bắt buộc sắp theo `user_id`** trước khi chia phần dư ([rule.md](rule.md) quy tắc 5).

Có `share_amount` thì dùng thẳng số đó.

---

## 3. Số dư quỹ <a id="3-so-du-quy"></a>

Mỗi nhóm có đúng một quỹ. Ảnh hưởng của từng khoản lên quỹ:

| `type` | `money_source` | Quỹ |
| :----- | :------------- | :-: |
| `EXPENSE` | `FUND` | − `amount` |
| `EXPENSE` | `PERSONAL` | không đổi |
| `CONTRIBUTION` | `PERSONAL` | + `amount` |
| `REFUND` | `FUND` | − `amount` |
| `ADJUSTMENT_UP` | `FUND` | + `amount` |
| `ADJUSTMENT_DOWN` | `FUND` | − `amount` |

```text
current_balance = Σ ảnh hưởng của mọi khoản CONFIRMED, chưa xoá
```

- Chỉ tính khoản **`CONFIRMED` và chưa xoá**.
- `current_balance` được cập nhật **lúc khoản chuyển sang `CONFIRMED`** (và hoàn tác lúc khoản rời khỏi `CONFIRMED` hoặc bị xoá), cùng transaction, bằng câu cộng dồn `current_balance = current_balance + :delta` sau khi **khoá dòng quỹ**.
- **Cơ chế khoá kết hợp bảo vệ tính nhất quán tài chính:**
    - **Khoá bi quan (Pessimistic Lock - `SELECT ... FOR UPDATE`):** áp dụng trên bản ghi quỹ `group_funds` khi cập nhật số dư, phê duyệt giao dịch và kiểm kê đối soát, ngăn ngừa triệt để lỗi Lost Update và xung đột dòng tiền đồng thời.
    - **Khoá lạc quan (Optimistic Lock - `@Version Long version`):** áp dụng trên bản ghi `group_transactions` để ngăn chặn xung đột khi hai người quản trị cùng duyệt hoặc chỉnh sửa một giao dịch tại cùng một thời điểm.
- Quỹ **không có số dư ban đầu**, luôn bắt đầu từ 0. Nhờ vậy mọi đồng trong quỹ đều thuộc phần của một người (mục 4).

---

## 4. Phần của mỗi người trong quỹ <a id="4-phan-cua-moi-nguoi"></a>

Phần của mỗi người luôn được tính, dù bật hay tắt tính thừa thiếu; cờ chỉ quyết định ứng dụng có hiện phần quyết toán hay không. Đây chính là số "còn bao nhiêu trong quỹ" (`net_balance`).

```text
phần của X = Σ CONTRIBUTION           có transactor_id = X
           + Σ EXPENSE (PERSONAL)      có transactor_id = X
           − Σ REFUND                  có transactor_id = X
           − Σ phần X chịu trong EXPENSE và ADJUSTMENT_DOWN
           + Σ phần X hưởng trong ADJUSTMENT_UP

Bất biến: Σ phần của mọi người = số dư quỹ
```

- Chỉ tính khoản `CONFIRMED` và chưa xoá. "Phần X chịu / hưởng" lấy theo mục 2.
- Số dương = **quỹ đang giữ tiền của X**; số âm = **X cần góp thêm**.

**"Phần trong quỹ" không phải là nợ giữa các thành viên.** Chênh lệch luôn giải quyết qua quỹ:

| Tình huống | Cách giải quyết |
| :--------- | :-------------- |
| Phần của C thấp | C **góp thêm** (`CONTRIBUTION`) |
| Phần của B cao vì đã trả tiền túi | Thủ quỹ **trả lại** B (`REFUND`, mục 12), hoặc để nguyên tới khi giải tán |
| Giải tán nhóm | Trả lại mỗi người đúng phần của họ (mục 14) |

---

## 5. Bảng tiền từng người và số cần nộp thêm <a id="5-so-tien-can-nop-them"></a>

**Bật tính thừa thiếu** — mỗi thành viên có: đã trả tiền túi, đã được hoàn, phải chịu, phần trong quỹ, cần nộp thêm.

**Tắt tính thừa thiếu** (không ai cần trả ai) — ứng dụng hiển thị: **mục tiêu**, **tổng tiền quỹ đang có**, và **số còn lại của mỗi người trong quỹ** (`net_balance`). Các trường còn lại vẫn có trong response nhưng ứng dụng không hiển thị; `total_needed_contribution` là `null`.

Không còn số "đã góp" riêng: mỗi người chỉ có một con số là phần còn lại trong quỹ ở mục 4.

**Cần nộp thêm** (chỉ khi bật tính thừa thiếu), thay cho việc gợi ý "X chuyển cho Y" ([rule.md](rule.md) quy tắc 16):

1. **Mọi nhóm, có hay không có mục tiêu:** `target` chỉ để xem tiến độ gom quỹ, **không** chia đầu người.
   - Phần trong quỹ âm → cần nộp đúng bằng `|phần của X|`.
   - Phần không âm → `0`.
2. **Tổng cả nhóm** (`total_needed_contribution`) = cộng `needed_contribution` của mọi người.
3. **Người mới vào nhóm** có phần trong quỹ = 0, nên cần nộp = 0 cho tới khi bị chia một khoản chi.

**Hiển thị thành viên trong bảng tiền:**

- **Thành viên `ACTIVE`:** luôn hiển thị.
- **Thành viên đã rời (`LEFT` / `REMOVED`):**
  - Phần trong quỹ bằng 0 → **ẩn hoàn toàn**, không hiển thị để bảng gọn gàng.
  - Phần trong quỹ $\ne 0$ → **hiển thị** (kèm trạng thái đã rời) với số tiền thừa/thiếu.
  - `cần nộp thêm`: nếu phần trong quỹ âm thì `cần nộp thêm = |phần trong quỹ|`; nếu không âm thì `0`.

---

## 6. Tạo nhóm <a id="6-tao-nhom"></a>

Trong **một** transaction CSDL:

1. Tạo `groups`, sinh `invite_code` (không thời hạn). `target` không nhập thì để `NULL`.
2. Tạo `group_members` cho người tạo: `role = OWNER`, `status = ACTIVE`, `joined_at = now()`.
3. Tạo **quỹ duy nhất** của nhóm trong `group_funds`, `keepper_id` = người tạo; số dư bắt đầu từ **0**. Bảng quỹ không có trường `status`.

Quỹ đã có sẵn tiền thì sau khi tạo nhóm, ghi mỗi người đã đưa tiền một khoản **góp quỹ** (mục 8). Không ghi thành số dư ban đầu, vì số đó không thuộc phần của ai: tổng phần lệch số dư quỹ ngay từ đầu, và khi bật tính thừa thiếu thì không trả lại được cho ai.

Thiếu bước 3 thì nhóm sinh ra đã vi phạm [rule.md](rule.md) quy tắc 14.

---

## 7. Ghi, xác nhận, sửa, xoá giao dịch <a id="7-ghi-khoan-chi"></a>

### 7.1 Ghi

**Màn ghi khoản chi** chỉ hỏi thêm ba thứ, cả ba có sẵn giá trị mặc định:

```text
Trả bằng     [ Tiền quỹ ▾ ]         ← money_source: FUND (mặc định) | PERSONAL (tiền bản thân)
Ai tham gia    Tất cả (3)  ›        ← mặc định tất cả; chạm vào để bỏ tích
Lúc           [ Bây giờ ▾ ]         ← occurred_at, mặc định lúc mở màn; ghi bù thì chọn ngày và giờ
```

**Service**, trong một transaction CSDL:

1. Nhóm đang lưu trữ → chặn ([rule.md](rule.md) quy tắc 26).
2. Kiểm tra theo [api.md](api.md) mục "Tạo giao dịch nhóm" (loại, nguồn tiền, thời điểm không ở tương lai, người trả, người tham gia, danh mục).
3. Danh sách người tham gia đúng bằng cả nhóm có mặt tại thời điểm đó → **bỏ trống danh sách**.
4. Xác định trạng thái:
   - Người ghi là **thủ quỹ hoặc chủ nhóm** → `CONFIRMED`, `reviewed_by` = người ghi, `reviewed_at = now()`.
   - Người khác → `PENDING`, `reviewed_by` / `reviewed_at` để trống.
5. Ghi một dòng `group_transactions`; nếu còn danh sách thì ghi các dòng `group_transaction_participants`.
6. Chỉ khi `CONFIRMED` → khoá dòng quỹ, áp ảnh hưởng theo bảng ở mục 3.

### 7.2 Xác nhận hoặc từ chối

Thủ quỹ hoặc chủ nhóm, trong một transaction CSDL:

| Thao tác | Điều kiện | Kết quả |
| :------- | :-------- | :------ |
| **Xác nhận** | Khoản đang `PENDING` | `status = CONFIRMED`, ghi `reviewed_by`, `reviewed_at`. Khoá dòng quỹ, áp ảnh hưởng theo mục 3 |
| **Từ chối** | Khoản đang `PENDING` | `status = REJECTED`, ghi `reviewed_by`, `reviewed_at`. Không đụng tới quỹ |

### 7.3 Sửa

Mục này dành cho `EXPENSE` / `CONTRIBUTION`, do người ghi hoặc chủ nhóm sửa. Sửa `REFUND` xem mục 12; `ADJUSTMENT_*` không sửa. Trong một transaction CSDL:

1. Khoản đang `CONFIRMED` → **hoàn tác** ảnh hưởng cũ lên quỹ.
2. Ghi giá trị mới; xoá và ghi lại người tham gia.
3. Người sửa là **thủ quỹ hoặc chủ nhóm** → `CONFIRMED`, áp ảnh hưởng mới lên quỹ.
   Người khác → `PENDING`, xoá `reviewed_by` / `reviewed_at`, **không** áp gì lên quỹ.
4. Khoản `REJECTED` được người ghi sửa → quay về `PENDING`.

### 7.4 Xoá

**Chỉ chủ nhóm** ([rule.md](rule.md) quy tắc 21). Đặt `deleted_at = now()`; nếu khoản đang `CONFIRMED` thì hoàn tác ảnh hưởng lên quỹ.

---

## 8. Góp quỹ <a id="8-gop-quy"></a>

B góp 1.000.000đ: ghi **một** dòng `CONTRIBUTION`, `money_source = PERSONAL`, `transactor_id = B`.

- B tự ghi → `PENDING`; thủ quỹ hoặc chủ nhóm xác nhận khi đã **thật sự nhận tiền** → `CONFIRMED`, lúc đó quỹ mới `+ amount`.
- Thủ quỹ tự ghi hộ B → `CONFIRMED` ngay, cộng quỹ ngay.

Khoản góp chỉ tồn tại ở sổ nhóm; ví cá nhân của B không bị hệ thống tự động đụng tới ([rule.md](rule.md) quyết định 4).

**Người đã rời góp bù tiền:** Cho phép tạo giao dịch `CONTRIBUTION` với `transactor_id` là thành viên đã rời (`LEFT` / `REMOVED`) nếu người đó đang có phần âm trong quỹ (để giải quyết nộp bù theo Cách A). Thao tác do thủ quỹ ghi hộ hoặc thành viên ghi và được xác nhận; khi nộp đủ, phần của người đó về 0 và tự động ẩn khỏi bảng tiền.

---

## 9. Kiểm kê quỹ <a id="9-kiem-ke-quy"></a>

Thủ quỹ hoặc chủ nhóm đếm tiền thật, bấm **"Kiểm kê"**, nhập con số thực tế. Thời điểm kiểm kê mặc định là lúc bấm, không được ở tương lai.

Trong **một** transaction CSDL:

1. **Khoá dòng quỹ** và đọc số dư mới nhất:

   ```sql
   SELECT current_balance FROM group_funds
    WHERE group_id = :groupId
    FOR UPDATE;
   ```

   Khoản xác nhận hoặc ghi phát sinh cùng lúc phải chờ kiểm kê xong.
2. `difference = actual_balance − current_balance` vừa đọc.

   | Kết quả | Hệ thống làm gì |
   | :------ | :-------------- |
   | `difference = 0` | Dừng, không sinh gì |
   | `difference < 0` | Sinh `ADJUSTMENT_DOWN`, `amount = abs(difference)` |
   | `difference > 0` | Sinh `ADJUSTMENT_UP`, `amount = difference` |

   Dòng kiểm kê: `money_source = FUND`, `transactor_id` = thủ quỹ, `created_by` = người bấm, **`status = CONFIRMED`** ngay, `reviewed_by` = người bấm.
3. Người bị bỏ tích (nếu có) → ghi các dòng người tham gia cho những người còn lại. Không bỏ tích ai → **không ghi dòng nào** (chia đều cả nhóm, vì thường không biết ai làm lệch).
4. **Cộng chênh lệch**, không ghi đè:

   ```sql
   UPDATE group_funds
      SET current_balance = current_balance + :difference
    WHERE group_id = :groupId;
   ```

Nhờ bước 1, ngay sau khi kiểm kê số dư bằng đúng số vừa đếm; nhờ bước 4, sổ luôn khớp số dư.

---

## 10. Rời nhóm <a id="10-roi-nhom"></a>

Áp dụng cho cả tự rời (`LEFT`) và bị chủ nhóm mời rời (`REMOVED`). Chủ nhóm muốn rời phải **chuyển quyền trước** (chủ cũ thành `MEMBER`, người nhận thành `OWNER`, cùng một transaction).

**Bật tính thừa thiếu thì phải tất toán trước khi rời** ([rule.md](rule.md) quy tắc 27):

| Kiểm tra | Không đạt thì |
| :------- | :------------ |
| Nhóm không còn khoản `PENDING` | Chặn — thủ quỹ / chủ nhóm xác nhận hoặc từ chối hết trước. Khoản chờ nếu xác nhận sau khi đã rời sẽ làm phần của người đó lệch khỏi 0 |
| Phần của người rời (mục 4) **bằng 0** | Chặn, báo số phần hiện tại. Phần **dương** → thủ quỹ ghi `REFUND` trả ra đúng số đó (mục 12). Phần **âm** → người đó **góp thêm** đúng số đó (mục 8), thủ quỹ xác nhận |

Tắt tính thừa thiếu thì không kiểm tra hai điều trên — không ai cần trả ai.

Hệ quả: người còn nợ mà không chịu góp thì chủ nhóm **không mời ra được**. Đó là chủ đích: cho ra thì những người ở lại gánh khoản nợ đó.

Qua kiểm tra thì, trong **một** transaction CSDL:

1. Người đó đang giữ quỹ → **chặn**, báo `409 GROUP_TREASURER_TRANSFER_REQUIRED`. Phải bàn giao quỹ (`PUT /v1/groups/{id}/fund-kepper`) trước.
2. Đặt `status` và `left_at = now()` cho bản ghi thành viên.
3. Hệ thống tự **`REJECTED`** mọi khoản `PENDING` có `created_by` = người rời: `reviewed_by` = người kích hoạt rời (chính mình hoặc chủ nhóm mời rời), `reviewed_at = now()`.

Các khoản đã ghi **không đổi**: khoản "cả nhóm" có thời điểm trước lúc rời vẫn tính người đó (mục 1), khoản sau lúc rời thì không.

**Ví dụ.** A, B, C mỗi người góp 1.000.000đ; B và C đi chơi riêng tiêu 2.400.000đ tiền quỹ. Phần: A 1.000.000 · B −200.000 · C −200.000; quỹ 600.000.

- C muốn rời → bị chặn, báo "còn thiếu 200.000đ". C góp 200.000đ, thủ quỹ xác nhận → phần C = 0 → rời được.
- A muốn rời → bị chặn, báo "quỹ còn giữ 1.000.000đ của bạn". Quỹ lúc này chỉ có 800.000đ nên thực tế cần đợi B góp nốt 200.000đ, rồi thủ quỹ ghi `REFUND` trả A 1.000.000đ → phần A = 0 → rời được.

**Xử lý khi phần của người đã rời bị lệch sau đó:**
Lúc rời phần đã bằng 0, nhưng nếu sau đó chủ nhóm sửa/xoá khoản cũ hoặc ghi bù làm phần của người đã rời $\ne 0$:

- Bảng tiền `/balances` tự động hiển thị người đó kèm số tiền thừa/thiếu.
- Nhóm giải quyết bằng cả 2 cách:
  - **Cách A (Người cũ nộp bù):** Người đó chuyển khoản trả nhóm $\to$ thủ quỹ ghi nhận khoản `CONTRIBUTION` với `transactor_id` = người đó $\to$ phần về 0 $\to$ tự động ẩn khỏi bảng tiền.
  - **Cách B (Nhóm tự gánh):** Người sửa mở khoản cũ ra và **bỏ tích người đã rời** khỏi danh sách người tham gia (`participants`) $\to$ khoản đó chỉ chia cho những người ở lại $\to$ phần của người đã rời về lại 0 $\to$ tự động ẩn.
  - Nếu phần **dương** (quỹ thừa tiền do xoá khoản chi cũ): Thủ quỹ trả lại cho người đó (`REFUND`).

---

## 11. Ví dụ kiểm chứng <a id="11-vi-du-kiem-chung"></a>

Nhóm A, B, C bật tính thừa thiếu, mỗi người góp 1.000.000đ, A giữ quỹ. Mọi khoản dưới đây đều đã `CONFIRMED`. B và C đi chơi riêng hết 600.000đ, **bỏ tích A** — khoản này có 2 dòng người tham gia (B, C), `share_amount` trống.

**Trường hợp 1 — tiêu bằng tiền quỹ (`FUND`):**

|          |    Đã nộp vào quỹ | Trả tiền túi | Phần phải chịu | **Phần trong quỹ** |
| :------- | --------: | -----------: | -------------: | -----------------: |
| A        | 1.000.000 |            0 |              0 |      **1.000.000** |
| B        | 1.000.000 |            0 |        300.000 |        **700.000** |
| C        | 1.000.000 |            0 |        300.000 |        **700.000** |
| **Tổng** |           |              |                |      **2.400.000** |

Quỹ: `3.000.000 − 600.000 = 2.400.000` ✅

**Trường hợp 2 — B trả bằng tiền túi (`PERSONAL`):**

|          |    Đã nộp vào quỹ | Trả tiền túi | Phần phải chịu | **Phần trong quỹ** |
| :------- | --------: | -----------: | -------------: | -----------------: |
| A        | 1.000.000 |            0 |              0 |      **1.000.000** |
| B        | 1.000.000 |      600.000 |        300.000 |      **1.300.000** |
| C        | 1.000.000 |            0 |        300.000 |        **700.000** |
| **Tổng** |           |              |                |      **3.000.000** |

Quỹ vẫn `3.000.000` ✅. **A không bị ảnh hưởng ở cả hai trường hợp.**

**Tiếp trường hợp 2 — A trả lại B 600.000đ (`REFUND`):** quỹ `3.000.000 − 600.000 = 2.400.000`; phần của B `1.300.000 − 600.000 = 700.000`. Kết quả **y hệt trường hợp 1** ✅.

**Kiểm kê tiếp theo trường hợp 1:** sổ báo 2.400.000đ, A đếm được 2.300.000đ.

| type | money_source | transactor_id | created_by | category_id | amount | note |
| :--- | :----------- | :------------ | :--------- | :---------- | -----: | :--- |
| `ADJUSTMENT_DOWN` | `FUND` | A | A | _NULL_ | 100.000 | Kiểm kê: sổ 2.400.000, thực tế 2.300.000 |

Không có dòng người tham gia. Lúc kiểm kê nhóm có A, B, C; sắp theo `user_id` rồi chia: A 33.334 · B 33.333 · C 33.333. Phần mới: **966.666 · 666.667 · 666.667**, tổng **2.300.000** ✅.

---

## 12. Quỹ trả tiền cho thành viên (`REFUND`) <a id="12-tra-lai-tien"></a>

Một loại giao dịch duy nhất cho mọi trường hợp tiền quỹ đi ra cho **một thành viên**, do thủ quỹ hoặc chủ nhóm ghi: hoàn tiền túi người đó đã trả hộ nhóm, hoặc trả lại tiền người đó đã góp (góp dư, rời nhóm, giải tán). Quỹ giảm và phần trong quỹ của người nhận giảm tương ứng.

Tất toán một người có phần dương: ghi `REFUND` đúng bằng phần của người đó.

Trong **một** transaction CSDL:

1. Người ghi phải là thủ quỹ hoặc chủ nhóm; nhóm không đang lưu trữ.
2. Người nhận là thành viên có mặt tại thời điểm trả; thời điểm không ở tương lai.
3. `amount` không vượt phần hiện có (`net_balance`) của người nhận, dù bật hay tắt tính thừa thiếu ([rule.md](rule.md) quy tắc 25).
4. Ghi một dòng `REFUND`: `money_source = FUND`, `transactor_id` = người nhận, `created_by` = người ghi, **`status = CONFIRMED`**, không danh mục, không người tham gia.
5. Khoá dòng quỹ, `current_balance = current_balance − amount`.

**Sửa** — chỉ thủ quỹ hiện tại hoặc chủ nhóm, không đổi loại, trong **một** transaction CSDL:

1. Kiểm tra như bước 1–3 ở trên, chỉ khi số tiền **tăng** hoặc **đổi người nhận** (giảm hoặc giữ nguyên thì luôn cho qua). Bước 3 tính **không kể chính khoản đang sửa**:
   - Giữ người nhận → `amount mới ≤ số hiện tại + amount cũ`.
   - Đổi người nhận → `amount mới ≤ số hiện tại của người mới`.
2. Khoá dòng quỹ, **hoàn tác** khoản cũ: `current_balance + amount cũ`.
3. Ghi giá trị mới (người nhận, số tiền, thời điểm, ghi chú); `reviewed_by` = người sửa, `reviewed_at = now()`. Khoản **vẫn `CONFIRMED`**.
4. **Áp** khoản mới: `current_balance − amount mới`.

---

## 13. Lưu trữ và mở lại nhóm <a id="13-luu-tru-nhom"></a>

**Lưu trữ** — chủ nhóm:

1. Nhóm đang `ACTIVE`, **không còn khoản `PENDING`** → nếu còn thì chặn, yêu cầu xác nhận hoặc từ chối hết trước.
2. `groups.status = ARCHIVED`.

**Khi đang lưu trữ:** chỉ cho xem; mọi thao tác ghi khác bị chặn ([rule.md](rule.md) mục 3.7).

**Mở lại** — chủ nhóm: `groups.status = ACTIVE`.

---

## 14. Xoá nhóm <a id="14-xoa-nhom"></a>

Chủ nhóm, trong **một** transaction CSDL:

1. Không còn khoản `PENDING`.
2. Số dư quỹ bằng 0.
3. **Bật tính thừa thiếu** → phần của mọi thành viên (kể cả thành viên đã rời) đều bằng 0. Nếu chưa, thủ quỹ phải **trả ra** (mục 12) cho từng người còn phần dương, và người còn phần âm phải **góp thêm** (người đã rời góp bù theo Cách A hoặc nhóm tự gánh theo Cách B).
4. `groups.status = DELETED`.

**Giải tán điển hình** (bật tính thừa thiếu): quỹ còn 2.400.000đ, phần A 1.000.000 · B 700.000 · C 700.000 → ghi 3 khoản `REFUND` đúng bằng phần mỗi người → quỹ 0, phần mọi người 0 → xoá được.
