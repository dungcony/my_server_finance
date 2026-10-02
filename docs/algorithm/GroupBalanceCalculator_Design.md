# Đặc tả tính toán bảng cân đối nhóm (Group Balance)

## Mục lục

- [1. Bài toán](#1-bài-toán)
- [2. Dữ liệu đầu vào](#2-dữ-liệu-đầu-vào)
    - [GroupTransaction](#grouptransaction)
    - [GroupTransactionParticipant](#grouptransactionparticipant)
    - [Xác định thành viên khi không có participant](#xác-định-thành-viên-khi-không-có-participant)
- [3. Mô hình tích lũy: 4 biến số mỗi thành viên](#3-mô-hình-tích-lũy-4-biến-số-mỗi-thành-viên)
- [4. Công thức tính và ý nghĩa chỉ số](#4-công-thức-tính-và-ý-nghĩa-chỉ-số)
- [5. Quy tắc xử lý từng loại giao dịch](#5-quy-tắc-xử-lý-từng-loại-giao-dịch)
- [6. Quy tắc phân bổ share](#6-quy-tắc-phân-bổ-share)
    - [Xác định danh sách người chịu](#xác-định-danh-sách-người-chịu)
    - [Xác định số tiền mỗi người chịu](#xác-định-số-tiền-mỗi-người-chịu)
    - [Chia đều và thuật toán luân phiên phần dư](#chia-đều-và-thuật-toán-luân-phiên-phần-dư)
- [7. Bất biến tài chính phải bảo toàn](#7-bất-biến-tài-chính-phải-bảo-toàn)
- [8. Xử lý lỗi bắt buộc (Fail-fast)](#8-xử-lý-lỗi-bắt-buộc-fail-fast)
- [9. Kiến trúc: Phân tách Service và Calculator](#9-kiến-trúc-phân-tách-service-và-calculator)
- [10. Ví dụ minh họa](#10-ví-dụ-minh-họa)
    - [Kịch bản 1: Nhóm 3 người A, B, C và kiểm tra bất biến quỹ](#kịch-bản-1-nhóm-3-người-a-b-c-và-kiểm-tra-bất-biến-quỹ)
    - [Kịch bản 2: Chia share hỗn hợp](#kịch-bản-2-chia-share-hỗn-hợp)

---

## 1. Bài toán

Một nhóm có nhiều thành viên cùng quản lý tài chính chung. Cần tính bảng cân đối tài chính để trả lời câu hỏi: **ai đang
nợ nhóm, ai đang được nhóm nợ, và bao nhiêu**.

Kết quả cuối cùng là một bảng gồm mỗi thành viên một dòng, chứa các chỉ số tài chính và số dư ròng (Net Balance) của họ.

**Phạm vi thành viên hiển thị trên bảng cân đối:**

- **Thành viên `ACTIVE`:** Luôn luôn hiển thị đầy đủ.
- **Cựu thành viên (`LEFT`, `REMOVED`):** Bắt buộc phải hiển thị nếu `Net Balance ≠ 0` (nhằm phục vụ việc thu hồi nợ
  hoặc hoàn trả tiền trước khi tất toán dứt điểm). Nếu `Net Balance = 0`, có thể ẩn khỏi báo cáo thường nhật.

---

## 2. Dữ liệu đầu vào

### GroupTransaction

Mỗi giao dịch có các trường liên quan đến tính toán:

| Trường        | Kiểu                 | Ý nghĩa                                                 |
|---------------|----------------------|---------------------------------------------------------|
| `id`          | `UUID`               | Khóa chính                                              | 
| `groupId`     | `UUID`               | Nhóm sở hữu giao dịch                                   |
| `userId`      | `UUID`               | Người thực hiện giao dịch                               |
| `type`        | `GTransactionType`   | Loại giao dịch (xem mục 5)                              |
| `amount`      | `Long`               | Số tiền (đơn vị nhỏ nhất, luôn > 0)                     |
| `moneySource` | `MoneySource`        | Nguồn tiền: `FUND` (quỹ) hoặc `PERSONAL` (tiền túi)     |
| `occurredAt`  | `Instant`            | Thời điểm giao dịch xảy ra                              |
| `status`      | `GTransactionStatus` | Chỉ tính giao dịch `CONFIRMED`                          |
| `deletedAt`   | `Instant` (nullable) | Chỉ tính giao dịch chưa bị xóa mềm (`deletedAt = null`) |

**Điều kiện lọc:** Chỉ các giao dịch có `status = CONFIRMED` và `deletedAt IS NULL` mới được đưa vào tính toán. Sắp xếp
theo `occurredAt ASC, createdAt ASC`.

### GroupTransactionParticipant

Mỗi participant liên kết với một giao dịch, xác định ai phải chịu phần chi phí:

| Trường               | Kiểu              | Ý nghĩa                                               |
|----------------------|-------------------|-------------------------------------------------------|
| `groupTransactionId` | `UUID`            | ID giao dịch                                          |
| `userId`             | `UUID`            | Người phải chịu share                                 |
| `shareAmount`        | `Long` (nullable) | Số tiền cụ thể người này phải chịu. `null` = chia đều |

**Ràng buộc dữ liệu (Validation):**

- Trong cùng một giao dịch, `userId` của participant không được phép trùng lặp (Unique constraint).
- Nếu `shareAmount` có giá trị thì bắt buộc phải `> 0`.

### Xác định thành viên khi không có participant

Khi giao dịch không có bản ghi participant nào (ví dụ chi tiêu chung cho cả nhóm từ quỹ):

1. Xác định danh sách thành viên có mặt tại thời điểm `occurredAt` (thỏa mãn `joinedAt <= occurredAt` và
   `(leftAt IS NULL OR leftAt > occurredAt)`).
2. Nếu danh sách này rỗng $\rightarrow$ **Bắt buộc ném lỗi (Throw exception)**. Tuyệt đối **không fallback về danh sách
   ACTIVE hiện tại**, vì sẽ gây sai lệch tài chính khi gán nợ cho những người mới gia nhập nhóm sau ngày giao dịch xảy
   ra.

---

## 3. Mô hình tích lũy: 4 biến số mỗi thành viên

Mỗi thành viên có một bộ tích lũy gồm 4 biến số, khởi tạo ban đầu bằng 0:

| Biến              | Ý nghĩa                                                                               |
|-------------------|---------------------------------------------------------------------------------------|
| `rawContribution` | Tổng tiền thành viên đã nộp vào quỹ chung của nhóm                                    |
| `paidOutOfPocket` | Tổng tiền thành viên tự bỏ tiền túi chi hộ nhóm (không lấy từ quỹ)                    |
| `refunded`        | Tổng tiền quỹ nhóm đã trả lại cho thành viên (hoàn tiền túi hoặc trả lại tiền đã góp) |
| `share`           | Tổng phần chi phí phân bổ mà thành viên có trách nhiệm phải chịu                      |

---

## 4. Công thức tính và ý nghĩa chỉ số

Từ 4 biến tích lũy, tính ra chỉ số cốt lõi duy nhất là số dư ròng, tức số tiền người đó còn trong quỹ:

```text
Net Balance = rawContribution + paidOutOfPocket − refunded − share
```

Không có số "đóng góp còn lại" hay "đã rút" riêng. Chỉ tiêu góp quỹ (`target`) chỉ để xem tiến độ, không chia đầu
người. Số cần nộp thêm của mỗi thành viên chỉ suy ra từ Net Balance:
$$\text{needed} = \max (0, -\text{netBalance})$$

### Ý nghĩa của "Số dư ròng" (Net Balance)

Phản ánh quan hệ công nợ giữa thành viên và nhóm tại thời điểm tính toán:

| Giá trị   | Ý nghĩa                                          |
|-----------|--------------------------------------------------|
| `Net > 0` | Nhóm đang nợ người này (người này được nhận lại) |
| `Net < 0` | Người này đang nợ nhóm (cần đóng bù thêm)        |
| `Net = 0` | Huề (đã tất toán xong)                           |

---

## 5. Quy tắc xử lý từng loại giao dịch

Duyệt tuần tự qua danh sách giao dịch. Với mỗi giao dịch, lấy bộ tích lũy của `userId` và cập nhật theo `type`:

| Loại transaction  | Hành động                                                                                                               |
|-------------------|-------------------------------------------------------------------------------------------------------------------------|
| `CONTRIBUTION`    | `rawContribution += amount`                                                                                             |
| `EXPENSE`         | Nếu `moneySource = PERSONAL` $\rightarrow$ `paidOutOfPocket += amount`. Sau đó **luôn** phân bổ share với `factor = +1` |
| `REFUND`          | `refunded += amount`                                                                                                    |
| `ADJUSTMENT_DOWN` | Phân bổ share với `factor = +1` (tăng phần phải chịu — quỹ thực tế hao hụt/ít hơn sổ sách)                              |
| `ADJUSTMENT_UP`   | Phân bổ share với `factor = −1` (giảm phần phải chịu — quỹ thực tế dôi ra/nhiều hơn sổ sách)                            |

> **Bắt buộc:** Switch statement phải có nhánh `default -> throw new IllegalStateException(...)` để fail-fast khi có
> loại giao dịch mới chưa được hỗ trợ.

---

## 6. Quy tắc phân bổ share

Khi giao dịch cần phân bổ chi phí (EXPENSE, ADJUSTMENT_DOWN, ADJUSTMENT_UP), thực hiện 2 bước: xác định **ai chịu** và
**chịu bao nhiêu**.

### Xác định danh sách người chịu

```text
Giao dịch có participant?
├── CÓ   → danh sách người chịu = các participant của giao dịch
└── KHÔNG → danh sách người chịu = thành viên có mặt tại occurredAt (nếu rỗng → THROW)
```

### Xác định số tiền mỗi người chịu

```text
Tất cả participant đều có shareAmount?
├── CÓ  → Validate: tổng shareAmount PHẢI BẰNG amount.
│         • Nếu tổng == amount: áp dụng đúng shareAmount cho từng người.
│         • Nếu tổng != amount: THROW EXCEPTION (fail-fast, không chấp nhận lệch tiền).
│
├── TẤT CẢ ĐỀU NULL → Chia đều amount cho tất cả participant.
│
└── HỖN HỢP (một số có shareAmount, một số null)
    • Tính: sumSpecified = tổng(shareAmount đã chỉ định)
    • Nếu sumSpecified >= amount: THROW EXCEPTION (dữ liệu bất hợp lý).
    • Nếu sumSpecified < amount:
      - Người có shareAmount: giữ nguyên giá trị đã chỉ định.
      - Người có shareAmount = null: chia đều số tiền còn lại (amount − sumSpecified) bằng cách áp dụng đúng thuật toán luân phiên phần dư bên dưới trên tập con các thành viên có shareAmount = null.
```

### Chia đều và thuật toán luân phiên phần dư

Khi chia đều `amount` cho `n` người (áp dụng cho toàn bộ danh sách khi tất cả đều null, hoặc cho tập con null-share
trong ca hỗn hợp):

```text
base = amount / n              (phần nguyên)
remainder = amount % n         (phần dư)
```

**Thuật toán phân bổ công bằng & ổn định (Fair & Deterministic):**

1. Sắp xếp danh sách `userId` (của nhóm người cần chia đều) theo thứ tự chuỗi (`UUID.toString()`) để có mảng cố định.
2. Xác định vị trí bắt đầu luân phiên dựa trên ID giao dịch:
   ```java
   int startIndex = Math.floorMod(txn.getId().hashCode(), n);
   ```
   > **Lưu ý kỹ thuật:** Sử dụng `Math.floorMod(hashCode, n)` thay vì `Math.abs(hashCode) % n` để triệt tiêu lỗi kinh
   điển khi `hashCode() == Integer.MIN_VALUE` (khi đó `Math.abs` bị tràn số và vẫn trả về số âm). `Math.floorMod` luôn
   đảm bảo kết quả nằm trong khoảng `[0, n - 1]`.
3. Phân bổ:
    - `remainder` người tại các vị trí `(startIndex + i) % n` (với `i = 0 .. remainder − 1`):
      $$\text{share} += (\text{base} + 1) \times \text{factor}$$
    - Các vị trí còn lại:
      $$\text{share} += \text{base} \times \text{factor}$$

**Ưu điểm:**

- **Tính xác định (Deterministic):** Cùng dữ liệu đầu vào luôn cho ra kết quả phân bổ giống hệt nhau khi tính toán lại.
- **Tính công bằng (Fairness):** Phần dư 1 đơn vị tiền lẻ được luân phiên rải đều qua các transaction khác nhau, tránh
  việc người có UUID nhỏ nhất luôn phải chịu thiệt thòi.

---

## 7. Bất biến tài chính phải bảo toàn

Sau khi duyệt hết tất cả giao dịch, bảng cân đối **bắt buộc phải thỏa mãn phương trình bảo toàn quỹ**:

$$\sum_{i} \text{NetBalance}_i = \text{Số dư tiền mặt thực tế trong Quỹ nhóm (Fund Balance)}$$

Trong đó:
$$\text{Fund Balance} = \sum \text{CONTRIBUTION} - \sum \text{REFUND} - \sum \text{EXPENSE}_{\text{FUND}} - \sum \text{ADJUSTMENT\_DOWN} + \sum \text{ADJUSTMENT\_UP}$$

### Chứng minh tính đúng đắn:

Ta có Net Balance của từng thành viên:
$$N_i = C_i + P_i - R_i - S_i$$
Lấy tổng tất cả thành viên:
$$\sum N_i = \sum C_i + \sum P_i - \sum R_i - \sum S_i$$
Vì tổng chi phí $\sum S_i$ được phân bổ từ các khoản chi hộ cá nhân $P$, chi từ quỹ $\text{EXPENSE}_{\text{FUND}}$, và
các điều chỉnh quỹ:
$$\sum S_i = \sum P_i + \sum \text{EXPENSE}_{\text{FUND}} + \sum \text{ADJUSTMENT\_DOWN} - \sum \text{ADJUSTMENT\_UP}$$
Thay $\sum S_i$ vào phương trình tổng Net Balance:
$$\sum N_i = \sum C_i - \sum R_i - \sum \text{EXPENSE}_{\text{FUND}} - \sum \text{ADJUSTMENT\_DOWN} + \sum \text{ADJUSTMENT\_UP} \equiv \text{Fund Balance}$$
Phần tiền túi chi hộ $\sum P_i$ tự động triệt tiêu hoàn toàn.

**Ý nghĩa khi kiểm thử (Unit Test):**

- Bất biến này **luôn luôn đúng ở mọi thời điểm** và mọi kịch bản giao dịch.
- Test case chỉ cần assert: `assertThat(memberBalances.totalNet()).isEqualTo(fund.getBalance())`.
- Nếu nhóm không có quỹ hoặc quỹ đã hết tiền (`Fund Balance = 0`), phương trình tự động trở
  về $\sum \text{NetBalance} = 0$.

---

## 8. Xử lý lỗi bắt buộc (Fail-fast)

Hệ thống tài chính tuyệt đối **không âm thầm bỏ qua dữ liệu hoặc log cảnh báo rồi tiếp tục**:

| Tình huống vi phạm                                                    | Hành vi xử lý                                          |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| Giao dịch cần phân bổ share nhưng không có ai chịu (danh sách rỗng)   | **Throw BusinessException**                            |
| Loại giao dịch mới chưa được khai báo trong switch                    | **Throw IllegalStateException** (fail-fast)            |
| $\sum \text{shareAmount} \neq \text{amount}$                          | **Throw BusinessException(PARTICIPANTS_SUM_MISMATCH)** |
| Chia hỗn hợp nhưng $\sum \text{shareAmount đã gán} \ge \text{amount}$ | **Throw BusinessException(PARTICIPANTS_SUM_MISMATCH)** |
| `shareAmount \le 0` hoặc trùng lặp `userId` trong cùng transaction    | **Throw BusinessException(INVALID_PARTICIPANT_DATA)**  |

---

## 9. Kiến trúc: Phân tách Service và Calculator

```text
GroupBalanceService (Service Layer)            GroupBalanceCalculator (Helper / Pure Engine)
───────────────────────────────────            ─────────────────────────────────────────────
• Truy vấn DB các giao dịch CONFIRMED          • Nhận dữ liệu thuần in-memory (không I/O)
• Batch load participants (1 query IN)         • Tự động lọc membership timeline in-memory
• Load toàn bộ timeline thành viên nhóm        • Thuần tính toán (Pure Calculation)
• Chuyển dữ liệu thuần sang Calculator         • Dễ dàng Unit Test độc lập không cần Mock DB
• Trả về bảng cân đối kết quả                  • Đảm bảo bất biến tài chính
```

### Cơ chế triệt tiêu N+1 Query:

1. **Giao dịch:** Lấy 1 lần danh sách giao dịch CONFIRMED chưa xóa mềm.
2. **Participants:** Batch load toàn bộ participants trong 1 câu query duy nhất:
   `findByGroupTransactionIdIn(txnIds)`.
3. **Timeline thành viên (Loại bỏ N+1 ẩn giấu):**
   Service query toàn bộ lịch sử thành viên của nhóm 1 lần duy nhất (`findByGroupId`) và chuyển sang danh sách dữ liệu
   thuần:
   `List<GroupMemberPeriod>` (`userId`, `joinedAt`, `leftAt`).
   Calculator nhận danh sách này và tự kiểm tra `joinedAt <= occurredAt && (leftAt == null || leftAt > occurredAt)` trực
   tiếp trên bộ nhớ (in-memory). Không sử dụng callback/lambda gọi ra Database.

---

## 10. Ví dụ minh họa

### Kịch bản 1: Nhóm 3 người A, B, C và kiểm tra bất biến quỹ

**Giao dịch 1:** A nộp tiền vào quỹ 600,000đ.

- `Type = CONTRIBUTION`, `userId = A`, `amount = 600,000`
- $\rightarrow$ `A.rawContribution += 600,000`. Quỹ nhóm tăng lên: **600,000đ**.

**Giao dịch 2:** B bỏ tiền túi mua đồ ăn 300,000đ, chia đều cho cả 3 người A, B, C.

- `Type = EXPENSE`, `moneySource = PERSONAL`, `amount = 300,000`
- $\rightarrow$ `B.paidOutOfPocket += 300,000`
- $\rightarrow$ Chia đều 3 người: `A.share += 100,000`, `B.share += 100,000`, `C.share += 100,000`. Quỹ nhóm giữ nguyên:
  **600,000đ**.

**Giao dịch 3:** Quỹ nhóm chi hoàn trả cho B 100,000đ (để bù 1 phần tiền túi B đã chi hộ).

- `Type = REFUND`, `userId = B`, `amount = 100,000`
- $\rightarrow$ `B.refunded += 100,000`. Quỹ nhóm còn lại: **500,000đ**.

**Bảng cân đối tài chính kết quả:**

| Thành viên | rawContribution | paidOutOfPocket | refunded |  share  | **Net Balance** |
|:----------:|:---------------:|:---------------:|:--------:|:-------:|:---------------:|
|   **A**    |     600,000     |        0        |    0     | 100,000 |  **+500,000**   |
|   **B**    |        0        |     300,000     | 100,000  | 100,000 |  **+100,000**   |
|   **C**    |        0        |        0        |    0     | 100,000 |  **−100,000**   |

**Kiểm tra bất biến bảo toàn quỹ:**
$$\sum \text{Net Balance} = 500,000 + 100,000 + (-100,000) = \mathbf{500,000đ}$$
$$\text{Số dư tiền thực tế trong Quỹ} = \text{Tiền nộp (600k)} - \text{Tiền hoàn trả (100k)} = \mathbf{500,000đ}$$
$$\sum \text{Net Balance} \equiv \text{Số dư quỹ} \implies \text{Chính xác 100%}$$

---

### Kịch bản 2: Chia share hỗn hợp

Giao dịch EXPENSE 500,000đ từ quỹ (`moneySource = FUND`) có 3 participant:

- A: `shareAmount = 200,000`
- B: `shareAmount = null`
- C: `shareAmount = null`

**Xử lý:**

1. Tổng phần đã chỉ định: $200,000 < 500,000$ (Hợp lệ).
2. Phần tiền còn lại: $500,000 - 200,000 = 300,000đ$.
3. Chia đều phần còn lại cho B và C: mỗi người $150,000đ$.

- $\rightarrow$ `A.share += 200,000`
- $\rightarrow$ `B.share += 150,000`
- $\rightarrow$ `C.share += 150,000`
- Tổng chi phí phân bổ = $200,000 + 150,000 + 150,000 = 500,000đ$ (Bảo toàn số tiền).
