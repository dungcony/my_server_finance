# KỊCH BẢN 1 — NHÓM CHI TRƯỚC, GOM SAU

## 1. Mô tả kịch bản

Nhóm bạn đi ăn uống, hát hò hoặc team-building nhưng **không thu tiền quỹ trước**.

Mọi thành viên có thể tự bỏ tiền túi để thanh toán các khoản chi phát sinh. Sau khi các khoản chi được ghi nhận, hệ
thống phải tính toán số tiền mỗi thành viên thực sự phải chịu và số tiền mỗi thành viên đã ứng trước.

Cuối cùng, hệ thống tạo bảng cân đối để xác định:

- Ai đang ứng nhiều hơn phần mình phải chịu.
- Ai đang ứng ít hơn phần mình phải chịu.
- Thành viên nào cần trả thêm tiền.
- Thành viên nào cần được nhận lại tiền.
- Tổng `Net Balance` của toàn bộ nhóm phải bằng **0**.

## 2. Lập nhóm

### 2.1. Tạo nhóm

A tạo nhóm đi chơi cuối tuần:

```text id="1xv4j7"
Tên nhóm: Team Building
Mục tiêu quỹ: 0 VNĐ
```

Nhóm không yêu cầu thành viên đóng quỹ trước.

### 2.2. Thêm thành viên

A thêm B và C vào nhóm.

Thành viên:

- A
- B
- C

### 2.3. Trạng thái quỹ ban đầu

Không có thành viên nào nộp tiền vào quỹ.

```text id="p7h7gy"
Số dư quỹ = 0 VNĐ
```

## 3. Phát sinh chi phí

### 3.1. Ngày 1 — A thanh toán tiền ăn

A dùng tiền cá nhân để thanh toán hóa đơn:

```text id="g6t1ae"
Hóa đơn: 900.000 VNĐ
moneySource: PERSONAL
Người thanh toán: A
Người tham gia: A, B, C
```

Chi phí được chia đều:

```text id="m6t9qk"
A: 300.000
B: 300.000
C: 300.000
```

Tuy nhiên A đã ứng trước toàn bộ **900.000 VNĐ**.

### 3.2. Ngày 1 — B thanh toán tiền karaoke

B dùng tiền cá nhân để thanh toán:

```text id="9gq5eu"
Hóa đơn: 600.000 VNĐ
moneySource: PERSONAL
Người thanh toán: B
Người tham gia: A, B, C
```

Chi phí mỗi người:

```text id="8y5xcm"
A: 200.000
B: 200.000
C: 200.000
```

B đã ứng trước toàn bộ **600.000 VNĐ**.

### 3.3. Ngày 2 — C thanh toán tiền taxi

C dùng tiền cá nhân:

```text id="3w0n0q"
Hóa đơn: 300.000 VNĐ
moneySource: PERSONAL
Người thanh toán: C
Người tham gia: A, C
```

Chỉ A và C chịu khoản chi này:

```text id="u4x9ph"
A: 150.000
C: 150.000
```

C đã ứng trước toàn bộ **300.000 VNĐ**.

## 4. Tổng hợp chi phí

Tổng số tiền đã chi:

```text id="w8a6yq"
900.000 + 600.000 + 300.000
= 1.800.000 VNĐ
```

### 4.1. Nghĩa vụ thực tế của từng thành viên

A chịu:

```text id="3yq4ra"
300.000 + 200.000 + 150.000
= 650.000 VNĐ
```

B chịu:

```text id="w6j1cv"
300.000 + 200.000
= 500.000 VNĐ
```

C chịu:

```text id="8k1xby"
300.000 + 200.000 + 150.000
= 650.000 VNĐ
```

Tổng:

```text id="d9z7i5"
650.000 + 500.000 + 650.000
= 1.800.000 VNĐ
```

### 4.2. Số tiền mỗi người đã ứng

| Thành viên |        Đã ứng |
|------------|--------------:|
| A          |       900.000 |
| B          |       600.000 |
| C          |       300.000 |
| **Tổng**   | **1.800.000** |

## 5. Tính số dư

Công thức:

```text id="k5h5qx"
Net Balance = Đã ứng - Phần chi phí phải chịu
```

Kết quả:

| Thành viên |  Đã ứng | Phải chịu |  Net Balance |
|------------|--------:|----------:|-------------:|
| A          | 900.000 |   650.000 | **+250.000** |
| B          | 600.000 |   500.000 | **+100.000** |
| C          | 300.000 |   650.000 | **-350.000** |

Diễn giải:

- A đang ứng thừa **250.000 VNĐ** → cần nhận lại.
- B đang ứng thừa **100.000 VNĐ** → cần nhận lại.
- C đang thiếu **350.000 VNĐ** → cần trả thêm.

Tổng:

```text id="5u7a0r"
+250.000 + 100.000 - 350.000
= 0
```

## 6. Quyết toán

Hệ thống xác định C cần thanh toán tổng cộng:

```text id="w5j0vn"
350.000 VNĐ
```

Có thể thực hiện bù trừ:

```text id="c8q2fo"
C → A: 250.000 VNĐ
C → B: 100.000 VNĐ
```

Sau khi thanh toán:

```text id="3m2v8c"
A: 0
B: 0
C: 0
```

Tất cả thành viên đã cân bằng.

## 7. Kết quả mong đợi

- Quỹ ban đầu = **0 VNĐ**.
- Không phát sinh `CONTRIBUTION`.
- Các khoản chi sử dụng `moneySource = PERSONAL`.
- Hệ thống ghi nhận người thực tế thanh toán từng hóa đơn.
- Hệ thống xác định đúng những thành viên tham gia từng khoản chi.
- A phải chịu **650.000 VNĐ**.
- B phải chịu **500.000 VNĐ**.
- C phải chịu **650.000 VNĐ**.
- A đã ứng **900.000 VNĐ** → được nhận lại **250.000 VNĐ**.
- B đã ứng **600.000 VNĐ** → được nhận lại **100.000 VNĐ**.
- C đã ứng **300.000 VNĐ** → phải trả thêm **350.000 VNĐ**.
- Tổng `Net Balance = 0`.
- Sau khi quyết toán, mọi thành viên đều có `Net Balance = 0`.