# User story ba loại nhóm — bài toán cụ thể

|  |  |
|---|---|
| Ngày viết | 10/09/2026 |
| Mục đích | **Ba câu chuyện có số liệu chạy được từ đầu đến cuối**, để nhìn ra ba loại nhóm thật sự khác nhau ở đâu |
| Trạng thái | 🤔 Tài liệu để đọc và quyết — **chưa chốt gì** |
| Đọc cùng | [16-BOI-CANH-NHOM.md](16-BOI-CANH-NHOM.md) · [17-DOI-CHIEU-USER-STORY-NHOM.md](17-DOI-CHIEU-USER-STORY-NHOM.md) · [15-CHOT-PHAM-VI-NHOM-GIA-DINH.md](15-CHOT-PHAM-VI-NHOM-GIA-DINH.md) |

> **Cách đọc file này.** Mỗi loại nhóm là một câu chuyện có nhân vật, có số tiền thật, chạy từ lúc
> lập nhóm tới lúc kết thúc. Sau mỗi câu chuyện là bảng "hệ thống phải làm gì" — đối chiếu với thứ
> đã có. Mục 5 là chỗ so ba câu chuyện với nhau và rút ra kết luận.

---

## 0. Từ vựng

| Từ | Nghĩa |
|---|---|
| **Ví chung** | Ví có `group_id` — cả nhóm nhìn thấy, cả nhóm ghi được |
| **Góp quỹ** | Bỏ tiền vào ví chung. Trong hệ thống là giao dịch `income` |
| **Chi từ quỹ** | Tiêu tiền của ví chung. Giao dịch `expense` |
| **Ứng trước** | Một người trả tiền túi mình cho cả nhóm, sẽ đòi lại sau. **Không** đi qua ví chung |
| **Chia tiền (split)** | Một khoản chi được phân bổ cho nhiều người, mỗi người chịu một phần |
| **Công nợ** | Kết quả của chia tiền — ai còn nợ ai bao nhiêu |
| **Quyết toán** | Trả nợ xong, đưa công nợ về 0 |
| **Thủ quỹ** | Người thực sự giữ tiền của nhóm |

---

# Câu chuyện 1 — Gia đình Minh

## 1.1 Nhân vật

| Người | Vai | Điều họ sợ nhất |
|---|---|---|
| **Minh** (38t) | Lập nhóm, giữ thẻ ngân hàng chung | Cuối tháng hụt tiền mà không biết vì sao |
| **Lan** (36t) | Vợ, đi chợ hằng ngày | Bị soi từng khoản mua sắm riêng của mình |
| **Hùng** (22t) | Con trai đi làm, góp tiền nhà | Góp rồi mà không biết tiền dùng vào việc gì |

## 1.2 Câu chuyện

**Ngày 1/9 — lập nhóm.** Minh tạo nhóm "Gia đình Minh". Hệ thống tạo sẵn ví chung
**"Quỹ gia đình"** số dư 0. Minh mời Lan và Hùng bằng mã `GDMINH-7K2P`.

Lan vào nhóm, thấy dòng chữ trấn an: *"Vào nhóm không chia sẻ dữ liệu cá nhân của bạn. Chỉ ví chung
mới hiển thị cho cả nhóm."* — đúng nỗi sợ của Lan.

**Ngày 2/9 — tiền vào quỹ.**

```
Minh chuyển vào quỹ    15.000.000đ   (lương)
Hùng chuyển vào quỹ     3.000.000đ   (góp tiền nhà)
                       ────────────
Quỹ gia đình           18.000.000đ
```

Minh mở màn nhóm, thấy quỹ có 18 triệu. Nhưng thực tế **tiền nằm ở hai chỗ**: 15 triệu trong thẻ
Vietcombank của Minh, 3 triệu tiền mặt Hùng đưa. Minh tạo thêm ví chung thứ hai:

```
Quỹ gia đình (Vietcombank)   15.000.000đ
Tiền mặt ở nhà                3.000.000đ
                             ────────────
Tổng quỹ chung               18.000.000đ
```

> 💡 Đây là lời giải cho câu **"ai cầm tiền, ai còn bao nhiêu"** — không cần nghiệp vụ mới, chỉ cần
> nhiều ví chung. Xem [16 mục 2.1](16-BOI-CANH-NHOM.md).

**Ngày 3–28/9 — chi tiêu hằng ngày.**

Lan đi chợ, mở app, ghi 3 giây: `250.000đ · Ăn uống · Quỹ gia đình`. Không phải hỏi Minh, không
phải nhắn Zalo.

Minh trả tiền điện qua app ngân hàng, ghi vào: `850.000đ · Điện nước`.

Hùng nhìn màn nhóm bất cứ lúc nào cũng thấy tiền mình góp đi đâu — **đúng nỗi sợ của Hùng đã giải**.

**Ngày 15/9 — Lan mua đồ riêng.** Lan mua mỹ phẩm 1.200.000đ bằng tiền riêng, ghi vào **ví cá nhân**
của mình. Minh **không thấy gì cả** — kể cả khi Minh là chủ nhóm. *Nỗi sợ của Lan đã giải.*

**Ngày 20/9 — ngân sách cảnh báo.** Đầu tháng Minh đặt ngân sách chung: Ăn uống 8 triệu/tháng. Ngày
20 đã tiêu 7,2 triệu. Cả ba người **cùng nhận** thông báo *"Ăn uống đã dùng 90% ngân sách tháng 9"*.
Lan biết để đi chợ tiết kiệm hơn — chứ không phải Minh phải nhắc.

**Ngày 21/9 — hiểu nhầm.** Minh thấy khoản `1.800.000đ · Ăn uống` do Lan ghi ngày 19. Thay vì nhắn
Zalo, Minh **bình luận ngay dưới khoản đó**: *"Khoản này là gì em?"*. Lan trả lời: *"Đặt cỗ giỗ ông
nội"*. Câu hỏi và câu trả lời **nằm ngay cạnh con số**, ba tháng sau mở lại vẫn thấy.

**Ngày 25/9 — ghi nhầm.** Hùng ghi nhầm 500.000đ thành 5.000.000đ rồi đi công tác, tắt máy. Số dư
quỹ hiển thị sai 4,5 triệu. **Minh (chủ nhóm) sửa được** khoản đó về đúng — không phải chờ Hùng về.

**Ngày 30/9 — xem lại tháng.**

```
Tháng 9, 2026
Thu    18.000.000đ
Chi    11.400.000đ
Còn     6.600.000đ

Chi theo danh mục
  Ăn uống        7.800.000đ  ████████░░  68%
  Điện nước      1.700.000đ  ██░░░░░░░░  15%
  Giáo dục       1.900.000đ  ██░░░░░░░░  17%

Ai góp bao nhiêu
  Minh          15.000.000đ  83%
  Hùng           3.000.000đ  17%
```

**Tháng 10, 11, 12…** — nhóm chạy tiếp. **Không có ngày kết thúc.**

## 1.3 Hệ thống phải làm gì

| # | Việc | Đã có? |
|---|---|---|
| F-01 | Tạo nhóm, tự tạo sẵn một ví chung | ⚠️ `api/10` có tạo nhóm, nhưng ghi rõ "**không** tự tạo ví chung" — đã chốt đổi ([15 Q1](15-CHOT-PHAM-VI-NHOM-GIA-DINH.md)) |
| F-02 | Mời bằng mã, trấn an người mới về riêng tư | ✅ `api/10` mục 5 |
| F-03 | Nhiều ví chung trong một nhóm, hiện rõ tiền nằm đâu | ✅ Schema cho phép, cần màn hình |
| F-04 | Ai cũng ghi giao dịch vào ví chung, dưới 5 giây | ✅ `api/04` + `group_id` |
| F-05 | Ví cá nhân tuyệt đối riêng, kể cả với chủ nhóm | ✅ Nguyên tắc 3, `api/10` mục 10 |
| F-06 | Ngân sách chung theo tháng, cảnh báo cho **cả nhóm** | ⚠️ `budgets.group_id` có sẵn; **cảnh báo gửi cho ai** chưa rõ |
| F-07 | Bình luận theo giao dịch | ❌ Mới hoàn toàn |
| F-08 | Chủ nhóm sửa được giao dịch người khác ghi | ✅ Đã chốt ([15 mục 3.3](15-CHOT-PHAM-VI-NHOM-GIA-DINH.md)) |
| F-09 | Tổng quan tháng: thu/chi, theo danh mục, ai góp bao nhiêu | ⚠️ `GET /groups/{id}/summary` có đặc tả, chưa code |
| F-10 | Nhóm chạy vô hạn, không có bước kết thúc | ✅ Không cần làm gì |

**Điều gia đình KHÔNG cần:** chia tiền, công nợ, quyết toán, thủ quỹ riêng, ngày kết thúc.

---

# Câu chuyện 2 — Nhóm bạn đi Đà Nẵng

## 2.1 Nhân vật

| Người | Vai | Điều họ sợ nhất |
|---|---|---|
| **An** | Rủ đi chơi, lập nhóm, lên lịch trình | Đứng ra tổ chức rồi ôm nợ |
| **Bình** | Giữ tiền quỹ (tài khoản của Bình) | Bị nghi là tiêu tiền quỹ vào việc riêng |
| **Cường** | Đi cùng | Góp nhiều hơn người khác mà không ai biết |
| **Dũng** | Đi cùng, hay quên | Bị đòi tiền mà không nhớ vì sao |

> ⚠️ **An lập nhóm nhưng Bình giữ tiền** — đây chính là chỗ *owner ≠ thủ quỹ*. Xem
> [16 mục 4](16-BOI-CANH-NHOM.md).

## 2.2 Câu chuyện

**Ngày 1/9 — lập nhóm có đích.** An tạo nhóm "Đà Nẵng 10/9", đặt **mục tiêu quỹ 24.000.000đ**
(4 người × 6 triệu). Chỉ định **Bình làm thủ quỹ**.

```
Đà Nẵng 10/9
Mục tiêu   24.000.000đ
Đang có             0đ    ░░░░░░░░░░   0%
Thủ quỹ    Bình
```

**Ngày 2–8/9 — góp quỹ, và đây là chỗ khác gia đình hẳn.**

```
2/9   An     chuyển  6.000.000đ  ✅
3/9   Bình   bỏ vào  6.000.000đ  ✅
5/9   Cường  chuyển  6.000.000đ  ✅
8/9   Dũng   ────────────────────  ❌ chưa góp

Đang có   18.000.000đ / 24.000.000đ   ███████░░░  75%
```

An mở app, **thấy ngay Dũng chưa góp**. Nhắn nhẹ trong phòng chat nhóm: *"Dũng ơi mai chuyển nhé,
mốt bay rồi"*. Dũng chuyển ngày 9/9 → quỹ đủ 24 triệu, thanh tiến độ đầy.

> 💡 **Đây là thứ gia đình không cần mà nhóm bạn rất cần:** ai đã góp, ai chưa. Hiện
> `member_contributions` **đã trả về** số tiền từng người góp — nhưng không có khái niệm *"phải góp
> bao nhiêu"* nên không tính được "còn thiếu". Xem mục 2.4.

**Ngày 10–13/9 — đi chơi, tiêu từ quỹ.**

```
10/9  Bình  Khách sạn 3 đêm    -9.000.000đ
10/9  An    Vé máy bay 4 người -8.000.000đ
11/9  Bình  Ăn tối hải sản     -2.400.000đ
12/9  Cường Taxi + vé tham quan-1.600.000đ
                               ────────────
      Quỹ còn                   3.000.000đ
```

Ai cũng ghi được, ai cũng thấy quỹ còn bao nhiêu **theo thời gian thực**. Không ai phải hỏi Bình
*"quỹ còn nhiêu?"* — **nỗi sợ của Bình đã giải**, vì mọi khoản Bình chi đều có ghi chép công khai.

**Ngày 12/9 — khoản chi ngoài quỹ.** Tối đó cả nhóm đi bar, quỹ không đủ, **An trả trước bằng thẻ
riêng 2.000.000đ**. Đây **không phải** chi từ quỹ — đây là **An ứng trước cho 4 người**.

```
Bar đêm 12/9        2.000.000đ
Người trả:          An
Chia đều 4 người:   500.000đ/người
→ Bình, Cường, Dũng mỗi người nợ An 500.000đ
```

> ⚠️ **Đây là ranh giới giữa hai bài toán.** "Chi từ quỹ" là giao dịch trên ví chung — đã có. "Một
> người ứng trước rồi chia cho cả nhóm" là **chia tiền** — chưa có gì, và không dùng lại được bảng
> `debts` ([17 mục 2.4](17-DOI-CHIEU-USER-STORY-NHOM.md)).

**Ngày 13/9 — Cường trả tiền phòng phát sinh.** Cường trả thêm 800.000đ tiền phòng, nhưng khoản này
**chỉ 2 người ở** (Cường và Dũng) → chia đôi, không chia bốn.

**Ngày 14/9 — về nhà, chốt sổ.**

```
QUỸ CHUNG
  Đã góp        24.000.000đ
  Đã chi        21.000.000đ
  Còn dư         3.000.000đ  →  chia lại 750.000đ/người

CÔNG NỢ NGOÀI QUỸ
  Bar 12/9    An ứng 2.000.000đ, chia 4    → B, C, D mỗi người nợ An 500.000đ
  Phòng 13/9  Cường ứng 800.000đ, chia 2   → Dũng nợ Cường 400.000đ

TỔNG HỢP TỪNG NGƯỜI
  An      nhận lại 750.000 + được trả 1.500.000 = +2.250.000đ
  Bình    nhận lại 750.000 − nợ An 500.000       =   +250.000đ
  Cường   nhận lại 750.000 − nợ An 500.000 + được trả 400.000 = +650.000đ
  Dũng    nhận lại 750.000 − nợ An 500.000 − nợ Cường 400.000 =  −150.000đ

GỢI Ý CHUYỂN TIỀN — ít lần nhất
  Dũng  →  An       150.000đ
  (quỹ)  →  An    2.100.000đ
  (quỹ)  →  Bình     250.000đ
  (quỹ)  →  Cường    650.000đ
```

Dũng chuyển 150k cho An, đánh dấu **đã trả**. Công nợ về 0.

> 💡 Không có bước tổng hợp này thì bốn người phải chuyển tiền chéo nhau **6 lần**. Có tối ưu thì
> còn **4 lần** — và không ai phải ngồi tính tay.

**Ngày 15/9 — giải tán.** An đóng nhóm. Số liệu **vẫn còn** trong CSDL (xoá mềm) — sang năm ai muốn
tra "chuyến Đà Nẵng năm ngoái hết bao nhiêu" vẫn xem được.

## 2.3 Hệ thống phải làm gì

| # | Việc | Đã có? |
|---|---|---|
| B-01 | Tạo nhóm kèm **mục tiêu quỹ** + thanh tiến độ | ❌ `savings_goals` thiếu `group_id` — việc **nhỏ**, có khuôn mẫu `wallets`/`budgets` |
| B-02 | Chỉ định **thủ quỹ** khác chủ nhóm | ❌ `CHECK (role IN ('owner','member'))` chỉ cho 2 vai |
| B-03 | Danh sách **ai đã góp, ai chưa** | ⚠️ Có số tiền góp, thiếu khái niệm "phải góp bao nhiêu" |
| B-04 | Ghi chi từ quỹ, ai cũng thấy số dư tức thì | ✅ Giống gia đình |
| B-05 | **Ứng trước rồi chia cho nhóm** | ❌ Mới hoàn toàn, ≥ 2 bảng |
| B-06 | Chia **không đều** — chỉ 2/4 người chịu | ❌ Cùng nhánh B-05 |
| B-07 | Tổng hợp công nợ chéo giữa các thành viên | ❌ Cùng nhánh B-05 |
| B-08 | **Tối ưu số lần chuyển tiền** | ❌ Thuật toán riêng |
| B-09 | Đánh dấu đã trả, đưa công nợ về 0 | ❌ Cùng nhánh B-05 |
| B-10 | Chia lại tiền dư khi kết thúc | ❌ Cùng nhánh B-05 |
| B-11 | Phòng chat nhóm | ❌ Mới hoàn toàn |
| B-12 | Giải tán nhưng giữ số liệu | ✅ Xoá mềm, đã chốt |

**Điều nhóm bạn KHÔNG cần:** ngân sách theo tháng, thống kê nhiều tháng, hoá đơn định kỳ.

## 2.4 Chỗ mập mờ lộ ra từ câu chuyện này

| # | Vấn đề | Ghi chú |
|---|---|---|
| M1 | **"Phải góp bao nhiêu" là dữ liệu gì?** | Để biết "Dũng chưa góp" thì hệ thống phải biết Dũng *phải* góp 6 triệu. Chia đều mục tiêu cho số thành viên? Hay đặt tay từng người? |
| M2 | **Tiền quỹ và tiền ứng trước là hai sổ khác nhau** | Trong câu chuyện có cả hai. Người dùng có phân biệt được không, hay sẽ nhầm lẫn? |
| M3 | **Thủ quỹ khác member ở quyền gì** | Câu chuyện cho thấy Bình *giữ tiền* nhưng An, Cường vẫn chi được từ quỹ. Vậy thủ quỹ chỉ là **nhãn hiển thị**? Xem [16 mục 4](16-BOI-CANH-NHOM.md) |

---

# Câu chuyện 3 — Bốn người ở ghép

## 3.1 Nhân vật

| Người | Vai | Điều họ sợ nhất |
|---|---|---|
| **Nam** | Đứng tên hợp đồng thuê, trả tiền nhà cho chủ | Ứng tiền nhà rồi bạn cùng phòng trả chậm |
| **Phúc** | Ở cả tháng | Trả tiền điện như nhau trong khi người khác về quê nửa tháng |
| **Quân** | Về quê nửa tháng 9 | Bị tính tiền điện cả tháng dù không ở |
| **Sơn** | Hay quên | Bị nhắc nợ giữa bữa cơm |

## 3.2 Câu chuyện

**Ngày 1/9 — lập nhóm.** Nam tạo nhóm "Trọ 25 Cầu Giấy", mời ba người.

**Khác cả hai loại trên:** nhóm này **không có quỹ chung**. Không ai bỏ tiền vào một chỗ. Mỗi tháng
Nam ứng trước, rồi ba người kia trả lại phần của mình.

**Ngày 1/9 — dựng hoá đơn định kỳ.** Nam tạo bốn khoản lặp hằng tháng:

```
Tiền nhà     6.000.000đ   ngày 5 hằng tháng    chia đều 4
Internet       300.000đ   ngày 10 hằng tháng   chia đều 4
Điện          (thay đổi)  ngày 15 hằng tháng   chia theo số ngày ở
Nước          (thay đổi)  ngày 15 hằng tháng   chia đều 4
```

**Ngày 5/9 — tiền nhà.** Hệ thống **tự sinh** khoản chi 6.000.000đ, Nam là người trả.

```
Tiền nhà tháng 9    6.000.000đ
Nam ứng trước
Chia đều 4          1.500.000đ/người
  Phúc  ❌ chưa trả
  Quân  ❌ chưa trả
  Sơn   ❌ chưa trả
```

Ba người nhận thông báo *"Bạn nợ 1.500.000đ tiền nhà tháng 9"*. Phúc và Quân trả trong ngày. Sơn
quên — **hệ thống nhắc**, Nam không phải mở miệng đòi. *Nỗi sợ của Sơn: bị đòi giữa bữa cơm — đã
giải, vì máy đòi chứ không phải người.*

**Ngày 15/9 — tiền điện, và đây là chỗ khác hẳn hai loại trên.**

Hoá đơn điện tháng 9 về: **900.000đ**. Nhưng Quân về quê từ 1/9 đến 15/9, chỉ ở **15 ngày**.

Chia đều thì Quân trả 225.000đ — Phúc thấy không công bằng. Chia **theo số ngày ở**:

```
Điện tháng 9   900.000đ

           Số ngày   Tỉ lệ    Phải trả
  Nam         30      33,3%   300.000đ
  Phúc        30      33,3%   300.000đ
  Quân        15      16,7%   150.000đ
  Sơn         30      33,3%   300.000đ
             ────                ────────
             105 ngày-người    900.000đ
```

Quân trả 150k thay vì 225k. *Nỗi sợ của Quân đã giải.* Phúc cũng hài lòng vì tiền chênh được chia
cho ba người ở đủ, không phải mình Phúc gánh.

> 💡 **Đây là thứ duy nhất trong ba câu chuyện mà cả gia đình lẫn nhóm bạn đều không cần.** Xem
> nhận định ở mục 5.3.

**Ngày 30/9 — nhìn lại tháng.**

```
Chi phí sinh hoạt tháng 9    7.450.000đ
  Tiền nhà      6.000.000đ
  Điện            900.000đ
  Nước            250.000đ
  Internet        300.000đ

So với tháng 8               6.980.000đ   ↑ 470.000đ
  Điện  8/2026   430.000đ  →  9/2026  900.000đ   ↑ 109%  ⚠️
```

Cả nhóm thấy ngay tiền điện tăng gấp đôi — bật lên nghi vấn *"có phải điều hoà hỏng không?"*. Đây
là **so sánh giữa các tháng**, thứ nhóm bạn đi chơi một chuyến không bao giờ cần.

**Tháng 10, 11…** — lặp lại y hệt. **Không có ngày kết thúc**, giống gia đình.

**Ngày 1/12 — Quân chuyển đi, Trung dọn vào.** Quân rời nhóm; các khoản Quân đã trả **vẫn còn trong
lịch sử**. Trung vào nhóm, từ tháng 12 mới bắt đầu chia cho Trung.

## 3.3 Hệ thống phải làm gì

| # | Việc | Đã có? | Giống ai |
|---|---|---|---|
| R-01 | Tạo nhóm, quản lý thành viên | ✅ | Cả hai |
| R-02 | **Hoá đơn định kỳ tự sinh khoản chi** | ⚠️ `recurring_transactions` có nhưng thiếu `group_id` | Gia đình cũng dùng được |
| R-03 | Một người ứng trước, chia cho cả nhóm | ❌ | **Y hệt nhóm bạn** (B-05) |
| R-04 | Theo dõi ai đã trả, ai chưa | ❌ | **Y hệt nhóm bạn** (B-09) |
| R-05 | **Chia theo số ngày ở** | ❌ | **Chỉ riêng loại này** |
| R-06 | Nhắc nợ tự động | ⚠️ `notifications` có, thiếu loại này | Chỉ loại này |
| R-07 | So sánh chi phí giữa các tháng | ⚠️ `api/06` có báo cáo cá nhân, chưa có chiều nhóm | **Y hệt gia đình** |
| R-08 | Không có quỹ chung | ✅ Không làm gì | Khác cả hai |
| R-09 | Người ra người vào giữa chừng | ✅ Đã chốt ([15 Q2](15-CHOT-PHAM-VI-NHOM-GIA-DINH.md)) | Cả hai |

---

# 5. So ba câu chuyện — kết luận

## 5.1 Bảng đối chiếu nhu cầu

| Nhu cầu | Gia đình | Nhóm bạn | Ở ghép |
|---|:---:|:---:|:---:|
| Quỹ chung (ví chung) | ✅ **lõi** | ✅ **lõi** | ❌ **không có** |
| Ngân sách theo tháng | ✅ **lõi** | ❌ | ⚠️ có thì tốt |
| Mục tiêu quỹ + tiến độ | ⚠️ có thì tốt | ✅ **lõi** | ❌ |
| Ai đã góp / ai chưa | ❌ | ✅ **lõi** | ✅ **lõi** |
| Chia tiền (split) | ❌ | ✅ **lõi** | ✅ **lõi** |
| Công nợ + quyết toán | ❌ | ✅ **lõi** | ✅ **lõi** |
| Hoá đơn định kỳ | ⚠️ có thì tốt | ❌ | ✅ **lõi** |
| Chia theo số ngày ở | ❌ | ❌ | ✅ **chỉ loại này** |
| Thủ quỹ tách khỏi owner | ❌ | ✅ **lõi** | ⚠️ Nam vừa là cả hai |
| So sánh nhiều tháng | ✅ **lõi** | ❌ | ✅ **lõi** |
| Có ngày kết thúc | ❌ | ✅ | ❌ |
| Bình luận theo giao dịch | ✅ | ✅ | ✅ |

## 5.2 Ba loại rơi vào hai trục, không phải ba

Đọc bảng trên theo cột thì thấy ba loại **không** phân biệt bằng ba tập tính năng riêng. Chúng phân
biệt bằng **hai câu hỏi độc lập**:

```
                 Có quỹ chung?
                 (tiền bỏ vào một chỗ)
                    CÓ          KHÔNG
                 ┌───────────┬───────────┐
    Có chia   CÓ │ Nhóm bạn  │  Ở ghép   │
    tiền?        │ đi chơi   │           │
              ───┼───────────┼───────────┤
           KHÔNG │ Gia đình  │  (vô nghĩa)│
                 └───────────┴───────────┘
```

**Ba loại nhóm = ba ô trong bảng hai chiều.** Ô thứ tư (không quỹ, không chia tiền) vô nghĩa vì
không còn gì để quản lý chung.

Hệ quả: **không cần ba luồng nghiệp vụ.** Chỉ cần hai khối tính năng bật/tắt độc lập:

| Khối | Gồm gì | Ai bật |
|---|---|---|
| **Quỹ chung** | Ví chung, góp quỹ, chi từ quỹ, mục tiêu quỹ, ngân sách, thủ quỹ | Gia đình, Nhóm bạn |
| **Chia tiền** | Ứng trước, chia đều/theo tỉ lệ/theo ngày, công nợ, quyết toán | Nhóm bạn, Ở ghép |

`group_type` chỉ là **preset bật sẵn tổ hợp nào** — đúng kết luận đã có ở
[16 mục 5 cách 1](16-BOI-CANH-NHOM.md) và [17 mục 4](17-DOI-CHIEU-USER-STORY-NHOM.md), giờ được ba
câu chuyện xác nhận độc lập.

## 5.3 Chia theo số ngày ở — thứ duy nhất chỉ một loại cần

Trong 12 nhu cầu ở bảng 5.1, **chỉ có một** thuộc riêng một loại: chia theo số ngày ở.

Nhưng nhìn kỹ, nó không phải tính năng riêng — nó là **kiểu chia thứ tư** bên cạnh chia đều, chia
theo tỉ lệ, chia theo số tiền:

| Kiểu chia | Đầu vào |
|---|---|
| Chia đều | (không cần gì) |
| Theo số tiền | Số tiền từng người |
| Theo tỉ lệ | % từng người |
| **Theo số ngày** | Số ngày từng người → quy ra tỉ lệ |

Kiểu thứ tư **quy về kiểu thứ ba**: 30/30/15/30 ngày → 33,3%/33,3%/16,7%/33,3%. Nên nếu đã làm chia
theo tỉ lệ thì thêm kiểu này gần như miễn phí — chỉ là một màn nhập số ngày rồi tự tính ra %.

**Kết luận: không có nhu cầu nào bắt buộc phải có loại nhóm thứ ba.**

## 5.4 Khối lượng — nhìn thẳng

| Khối | Trạng thái | Ước lượng |
|---|---|---|
| **Nền tảng nhóm** (tạo/mời/đuổi/rời/quyền) | ❌ Backend chưa có package `group/` | Lớn — 12 endpoint |
| **Quỹ chung** (ví, giao dịch, ngân sách, tổng quan) | ⚠️ Schema có, backend chưa | Vừa |
| **Mục tiêu quỹ** | ❌ Thiếu `group_id` ở `savings_goals` | **Nhỏ** — có khuôn mẫu |
| **Hoá đơn định kỳ nhóm** | ❌ Thiếu `group_id` ở `recurring_transactions` | **Nhỏ** — nhưng còn câu hỏi "thuộc về ai" |
| **Chia tiền + công nợ + quyết toán** | ❌ Không có gì | **Lớn** — ≥ 2 bảng mới, thuật toán tối ưu |
| **Thủ quỹ** | ❌ `CHECK` chỉ cho 2 vai | Nhỏ nếu chỉ là nhãn; vừa nếu gắn quyền |
| **Chat + bình luận realtime** | ❌ Không có gì, `pom.xml` chưa có WebSocket | **Lớn** |
| **App: tab thứ 5 + toàn bộ màn** | ❌ Không có gì | **Lớn** |

**Ba câu chuyện dùng chung 60% khối lượng** (nền tảng nhóm + app). Phần riêng chỉ là hai khối
quỹ chung và chia tiền.

## 5.5 Đề xuất thứ tự

| Cụm | Nội dung | Câu chuyện nào chạy được |
|---|---|---|
| **03** | Nền tảng nhóm + quỹ chung + app + bình luận | **Gia đình chạy đủ.** Nhóm bạn chạy được phần góp quỹ, thiếu chốt sổ |
| **04** | Chia tiền + công nợ + quyết toán | **Nhóm bạn và Ở ghép chạy đủ** |
| **05** | Chat realtime + hoá đơn định kỳ nhóm + mục tiêu quỹ | Hoàn thiện cả ba |

Chọn cách này vì **cụm 03 đã cho ra một câu chuyện chạy trọn vẹn** (gia đình), nghiệm thu được
ngay, thay vì ba câu chuyện đều dở dang.

---

# 6. Câu hỏi rút ra từ ba câu chuyện

Bổ sung vào 13 câu đã có ở [16 mục 7](16-BOI-CANH-NHOM.md) và [17 mục 6](17-DOI-CHIEU-USER-STORY-NHOM.md):

| # | Câu hỏi | Từ đâu ra |
|---|---|---|
| C14 | **"Phải góp bao nhiêu"** lấy ở đâu — chia đều mục tiêu, hay đặt tay từng người? | Câu chuyện 2, M1 |
| C15 | Người dùng có phân biệt được **"chi từ quỹ"** và **"ứng trước rồi chia"** không? Hai thứ này trong cùng một màn hình sẽ rất dễ nhầm | Câu chuyện 2, M2 |
| C16 | Nhóm **không có quỹ chung** (ở ghép) — màn hình nhóm hiện gì ở chỗ đáng lẽ là số dư? | Câu chuyện 3 |
| C17 | Cảnh báo vượt ngân sách chung **gửi cho ai** — chỉ người tạo, hay cả nhóm? | Câu chuyện 1, F-06 |
| C18 | Hoá đơn định kỳ nhóm: người tạo rời nhóm thì **còn chạy không**? | Câu chuyện 3, R-02 |
| C19 | Có cho **đổi `group_type`** sau khi tạo không? (nhóm bạn đi chơi xong muốn giữ lại thành nhóm chung quỹ) | Mục 5.2 |
