# KỊCH BẢN 3 — QUỸ CỐ ĐỊNH KHÔNG QUYẾT TOÁN

## 1. Mô tả kịch bản

Nhóm sử dụng quỹ cho mục đích chung như:

- Quỹ khuyến học.
- Quỹ từ thiện.
- Quỹ công đoàn.
- Quỹ lớp.
- Quỹ hoạt động chung.

Quỹ có một mục tiêu cần đạt, nhưng **không sử dụng cơ chế quyết toán giữa các thành viên**.

Cấu hình nhóm:

```text
is_settlement_enabled = false
```

Điều này có nghĩa:

- Tiền thành viên đã đóng trở thành tài sản chung của quỹ.
- Không tính thành viên nào đang nợ hoặc đang được nhận lại tiền.
- Không chia lại tiền thừa cho từng thành viên.
- Không tính `Net Balance` cho từng thành viên.
- Không thực hiện quyết toán cá nhân khi quỹ phát sinh chi phí.
- Hệ thống chỉ theo dõi số tiền đã đóng, số tiền đã chi và số tiền còn lại trong quỹ.

## 2. Lập nhóm

### 2.1. Tạo quỹ

A tạo quỹ khuyến học của lớp:

```text
Tên quỹ: Quỹ khuyến học lớp 12A
Mục tiêu: 20.000.000 VNĐ
is_settlement_enabled: false
```

### 2.2. Thêm thành viên

A thêm B, C và D vào quỹ.

Thành viên:

- A
- B
- C
- D

### 2.3. Trạng thái quỹ ban đầu

Chưa có thành viên nào đóng tiền.

```text
Target: 20.000.000
Total contributed: 0
Total expense: 0
Fund balance: 0
```

## 3. Giai đoạn 1 — Thành viên đóng tiền

### 3.1. A đóng quỹ

A đóng:

```text
1.000.000 VNĐ
```

Ghi nhận:

```text
CONTRIBUTION
A: 1.000.000
```

Quỹ:

```text
Total contributed: 1.000.000
Fund balance: 1.000.000
```

### 3.2. B đóng quỹ

B đóng:

```text
2.000.000 VNĐ
```

Quỹ:

```text
Total contributed: 3.000.000
Fund balance: 3.000.000
```

### 3.3. C đóng quỹ

C đóng:

```text
1.500.000 VNĐ
```

Quỹ:

```text
Total contributed: 4.500.000
Fund balance: 4.500.000
```

### 3.4. D đóng quỹ

D đóng:

```text
500.000 VNĐ
```

Quỹ:

```text
Total contributed: 5.000.000
Fund balance: 5.000.000
```

## 4. Theo dõi tiến độ mục tiêu

Mục tiêu của quỹ:

```text
20.000.000 VNĐ
```

Tổng tiền đã đóng:

```text
5.000.000 VNĐ
```

Số tiền còn thiếu:

```text
20.000.000 - 5.000.000
= 15.000.000 VNĐ
```

Tiến độ:

```text
5.000.000 / 20.000.000
= 25%
```

Hệ thống hiển thị:

| Thành viên |       Đã đóng |
|------------|--------------:|
| A          |     1.000.000 |
| B          |     2.000.000 |
| C          |     1.500.000 |
| D          |       500.000 |
| **Tổng**   | **5.000.000** |

## 5. Giai đoạn 2 — Quỹ phát sinh chi phí

### 5.1. Chi học bổng

Quỹ sử dụng tiền để trao học bổng:

```text
EXPENSE
moneySource = FUND
Số tiền: 2.000.000 VNĐ
```

Khoản chi này được trừ trực tiếp khỏi quỹ.

```text
Fund balance:
5.000.000 - 2.000.000
= 3.000.000 VNĐ
```

### 5.2. Chi mua sách

Quỹ tiếp tục chi:

```text
EXPENSE
moneySource = FUND
Số tiền: 1.000.000 VNĐ
```

Quỹ còn:

```text
3.000.000 - 1.000.000
= 2.000.000 VNĐ
```

## 6. Theo dõi lịch sử quỹ

Hệ thống phải ghi nhận được toàn bộ lịch sử:

| Loại         | Thành viên |    Số tiền |
|--------------|------------|-----------:|
| CONTRIBUTION | A          | +1.000.000 |
| CONTRIBUTION | B          | +2.000.000 |
| CONTRIBUTION | C          | +1.500.000 |
| CONTRIBUTION | D          |   +500.000 |
| EXPENSE      | Quỹ        | -2.000.000 |
| EXPENSE      | Quỹ        | -1.000.000 |

Tổng tiền đã đóng:

```text
5.000.000 VNĐ
```

Tổng tiền đã chi:

```text
3.000.000 VNĐ
```

Số dư quỹ:

```text
5.000.000 - 3.000.000
= 2.000.000 VNĐ
```

## 7. Không thực hiện quyết toán thành viên

Tại thời điểm này:

```text
A đã đóng: 1.000.000
B đã đóng: 2.000.000
C đã đóng: 1.500.000
D đã đóng:   500.000
```

Nhưng hệ thống **không thực hiện**:

```text
A phải chịu bao nhiêu
B phải chịu bao nhiêu
C phải chịu bao nhiêu
D phải chịu bao nhiêu
```

và cũng không tính:

```text
Net Balance
Amount Owed
Amount To Receive
Settlement
```

Ví dụ, B đã đóng nhiều hơn D không có nghĩa B được nhận lại phần chênh lệch.

Số tiền B đã đóng là một phần tài sản chung của quỹ.

## 8. Quỹ tiếp tục hoạt động

Sau khi chi 3.000.000 VNĐ, quỹ còn:

```text
2.000.000 VNĐ
```

Quỹ vẫn tiếp tục tồn tại.

Các thành viên có thể tiếp tục đóng thêm tiền:

```text
CONTRIBUTION
```

và quỹ có thể tiếp tục phát sinh:

```text
EXPENSE
moneySource = FUND
```

Ví dụ A tiếp tục đóng:

```text
1.000.000 VNĐ
```

Quỹ:

```text
2.000.000 + 1.000.000
= 3.000.000 VNĐ
```

Tổng tiền A đã đóng trong toàn bộ vòng đời quỹ:

```text
1.000.000 + 1.000.000
= 2.000.000 VNĐ
```

## 9. Kết quả mong đợi

- `is_settlement_enabled = false`.
- Mục tiêu quỹ = **20.000.000 VNĐ**.
- Tổng tiền đã đóng ban đầu = **5.000.000 VNĐ**.
- Quỹ đạt **25%** mục tiêu trước khi phát sinh chi phí.
- A đã đóng **1.000.000 VNĐ**.
- B đã đóng **2.000.000 VNĐ**.
- C đã đóng **1.500.000 VNĐ**.
- D đã đóng **500.000 VNĐ**.
- Quỹ chi **2.000.000 VNĐ** cho học bổng.
- Quỹ chi **1.000.000 VNĐ** cho sách.
- Tổng chi = **3.000.000 VNĐ**.
- Số dư quỹ = **2.000.000 VNĐ**.
- Các khoản `EXPENSE` sử dụng `moneySource = FUND`.
- Không phát sinh `SETTLEMENT`.
- Không tính `Net Balance` cho thành viên.
- Không có thành viên nào được nhận lại tiền chỉ vì đã đóng nhiều hơn thành viên khác.
- Tiền còn lại **2.000.000 VNĐ** tiếp tục thuộc về quỹ.
- Thành viên có thể tiếp tục đóng tiền và quỹ tiếp tục hoạt động.
- Hệ thống chỉ cần đảm bảo:

```text
Fund Balance
=
Total Contributions
-
Total Fund Expenses
```

- Thành viên chỉ cần theo dõi:
    - `net_balance` (số mỗi người còn trong quỹ)
    - lịch sử đóng tiền
    - lịch sử chi tiêu của quỹ
    - số dư quỹ
    - tiến độ đạt `target`