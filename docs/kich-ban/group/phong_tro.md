# KỊCH BẢN 2 — QUỸ PHÒNG TRỌ, CĂN HỘ CHUNG DÀI HẠN

## 1. Mô tả kịch bản

A, B và C sống chung trong một căn hộ trong nhiều tháng.

Nhóm duy trì một quỹ chung để thanh toán các chi phí sinh hoạt định kỳ như:

- Tiền thuê nhà.
- Tiền điện.
- Tiền nước.
- Internet.
- Nhu yếu phẩm.
- Các khoản chi phí chung khác.

Khác với nhóm đi chơi ngắn hạn, nhóm **không quyết toán và giải tán quỹ vào cuối mỗi tháng**.

Số dư cuối tháng được giữ lại và chuyển sang tháng tiếp theo.

Đầu mỗi tháng, hệ thống xác định số tiền cần đóng thêm dựa trên:

- Số dư quỹ hiện tại.
- Chi phí dự kiến hoặc mục tiêu quỹ của tháng.
- Phần đóng góp của từng thành viên.

## 2. Lập nhóm

### 2.1. Tạo nhóm

A tạo nhóm:

```text id="u4qz8r"
Tên nhóm: Căn hộ 123
Mục tiêu quỹ tháng: 6.000.000 VNĐ
```

### 2.2. Thêm thành viên

A thêm:

- B
- C

Thành viên:

- A
- B
- C

### 2.3. Chỉ định thủ quỹ

A chỉ định B làm thủ quỹ.

```text id="g4t6r0"
Chủ nhóm: A
Thủ quỹ: B
```

## 3. Tháng 1 — Nộp quỹ

### 3.1. Thành viên đóng quỹ

A, B và C mỗi người đóng:

```text id="1t8y2u"
2.000.000 VNĐ
```

Tổng quỹ:

```text id="n4b6yr"
A: 2.000.000
B: 2.000.000
C: 2.000.000
----------------
Tổng: 6.000.000 VNĐ
```

## 4. Tháng 1 — Phát sinh chi phí

### 4.1. Tiền thuê nhà

Thủ quỹ thanh toán tiền thuê nhà:

```text id="f7d5v0"
2.400.000 VNĐ
```

Khoản chi áp dụng cho:

- A
- B
- C

Mỗi người chịu:

```text id="b8r2nm"
2.400.000 / 3
= 800.000 VNĐ
```

### 4.2. Tiền điện

Chi phí điện:

```text id="c6p8ws"
600.000 VNĐ
```

Chia đều cho A, B và C:

```text id="q9y4u2"
200.000 VNĐ/người
```

### 4.3. Tiền nước

Chi phí nước:

```text id="j3v5ka"
300.000 VNĐ
```

Chia đều:

```text id="v2r8mn"
100.000 VNĐ/người
```

### 4.4. Internet

Chi phí Internet:

```text id="x5k7dp"
300.000 VNĐ
```

Chia đều:

```text id="m8q2zs"
100.000 VNĐ/người
```

## 5. Tổng kết tháng 1

Tổng chi:

```text id="q3n6hv"
2.400.000
+ 600.000
+ 300.000
+ 300.000
= 3.600.000 VNĐ
```

Quỹ đầu tháng:

```text id="0v4h3x"
6.000.000 VNĐ
```

Số dư cuối tháng:

```text id="r9k2fz"
6.000.000 - 3.600.000
= 2.400.000 VNĐ
```

Phần chi phí của mỗi thành viên:

```text id="5h8w1c"
A: 1.200.000
B: 1.200.000
C: 1.200.000
```

## 6. Chuyển sang tháng 2

### 6.1. Không quyết toán quỹ

Cuối tháng 1:

> Nhóm không thực hiện `REFUND` toàn bộ số dư.

Số dư:

```text id="w1s6qz"
2.400.000 VNĐ
```

được giữ nguyên trong quỹ và chuyển sang tháng 2.

### 6.2. Xác định số tiền cần đóng thêm

Mục tiêu quỹ tháng 2:

```text id="r7p4mn"
6.000.000 VNĐ
```

Số dư chuyển từ tháng 1:

```text id="k8v3dx"
2.400.000 VNĐ
```

Số tiền cần bổ sung:

```text id="n2m6qa"
6.000.000 - 2.400.000
= 3.600.000 VNĐ
```

Nếu tiếp tục chia đều cho A, B và C:

```text id="s5z9pc"
3.600.000 / 3
= 1.200.000 VNĐ/người
```

Do đó:

| Thành viên | Đóng thêm tháng 2 |
|------------|------------------:|
| A          |         1.200.000 |
| B          |         1.200.000 |
| C          |         1.200.000 |
| **Tổng**   |     **3.600.000** |

Sau khi đóng:

```text id="v8q3lr"
2.400.000 + 3.600.000
= 6.000.000 VNĐ
```

## 7. Tháng 2 — Tiếp tục sử dụng quỹ

Nhóm tiếp tục thanh toán các khoản chi phí sinh hoạt từ quỹ.

Ví dụ:

- Tiền thuê nhà.
- Tiền điện.
- Tiền nước.
- Internet.
- Chi phí sinh hoạt chung.

Các khoản chi của tháng 2 được ghi nhận bình thường và làm giảm số dư quỹ.

Thành viên không được xem là đã rút khỏi quỹ chỉ vì kết thúc tháng.

## 8. Kết quả mong đợi

- Quỹ ban đầu của nhóm = **0 VNĐ**.
- A, B, C đóng quỹ tháng 1, mỗi người **2.000.000 VNĐ**.
- Tổng quỹ tháng 1 = **6.000.000 VNĐ**.
- Tổng chi tháng 1 = **3.600.000 VNĐ**.
- Số dư cuối tháng 1 = **2.400.000 VNĐ**.
- Không thực hiện `REFUND` toàn bộ quỹ cuối tháng.
- **2.400.000 VNĐ** được chuyển sang tháng 2.
- Mục tiêu quỹ tháng 2 = **6.000.000 VNĐ**.
- Số tiền cần đóng bổ sung = **3.600.000 VNĐ**.
- Nếu chia đều, A, B và C mỗi người đóng thêm **1.200.000 VNĐ**.
- Sau khi đóng bổ sung, quỹ tháng 2 = **6.000.000 VNĐ**.
- Các thành viên vẫn thuộc cùng một nhóm và quỹ tiếp tục được sử dụng qua nhiều chu kỳ.
- Cuối tháng chỉ kết thúc **chu kỳ tài chính**, không kết thúc quỹ hoặc giải tán nhóm.