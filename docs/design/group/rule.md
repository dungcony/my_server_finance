# QUY TẮC NGHIỆP VỤ — PHÂN HỆ NHÓM CHUNG QUỸ

> **File này trả lời:** hệ thống **phải** và **không được** làm gì.
> Kịch bản Use Case → [use-case.md](use-case.md) · Cấu trúc bảng, cột, khoá → [lop-thuc-the.md](lop-thuc-the.md) · Luồng xử lý và cách tính → [pipeline.md](pipeline.md) · Endpoint → [api.md](api.md)

## 📑 MỤC LỤC

- [1. Quyết định nền tảng](#1-quyet-dinh-nen-tang)
- [2. Quy tắc bất biến](#2-quy-tac-bat-bien)
- [3. Quy tắc theo từng đối tượng](#3-quy-tac-theo-tung-doi-tuong)
  - [3.1 Nhóm](#31-nhom)
  - [3.2 Thành viên](#32-thanh-vien)
  - [3.3 Quỹ](#33-quy)
  - [3.4 Giao dịch](#34-giao-dich)
  - [3.5 Người tham gia](#35-nguoi-tham-gia)
  - [3.6 Tham chiếu sang module khác](#36-tham-chieu)
  - [3.7 Lưu trữ nhóm](#37-luu-tru-nhom)
- [4. Đã chốt](#4-da-chot)
- [5. Chưa chốt và để sau](#5-chua-chot-va-de-sau)
  - [5.1 Để họp sau](#51-de-hop-sau)

---

# 1. Quyết định nền tảng <a id="1-quyet-dinh-nen-tang"></a>

|   #   | Quyết định                                                                                                                                                                             | Lý do                                                                                                                                                      |
| :---: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1** | **Sổ nhóm độc lập.** Quỹ nhóm (`group_funds`) và giao dịch nhóm (`group_transactions`) là bảng riêng, **không dùng chung** với ví và giao dịch cá nhân                                 | Dữ liệu cá nhân và dữ liệu nhóm tách nhau **bằng chính cấu trúc**, không phụ thuộc việc nhớ thêm điều kiện lọc                                             |
| **2** | **Vắng người tham gia = cả nhóm.** Khoản không ghi người tham gia nào nghĩa là chia cho **mọi thành viên có mặt tại thời điểm giao dịch**. Chỉ khi bỏ tích bớt người mới ghi danh sách | Phần lớn khoản chi là của chung. Cái giá: `group_members` phải **giữ lịch sử vào và rời nhóm**                                                             |
| **3** | **Không lưu "ai nợ ai".** Phần của từng người trong quỹ được **tính khi đọc** từ các giao dịch                                                                                         | Sửa hay xoá một khoản thì mọi con số tự đúng lại                                                                                                           |
| **4** | **Phân hệ nhóm không ghi sang sổ cá nhân.** Góp quỹ hay trả tiền túi chỉ ghi ở sổ nhóm                                                                                                 | Đồng bộ hai sổ kéo theo sửa bảng giao dịch cá nhân và bài toán báo cáo cá nhân bị đếm đôi. Để sau — mục 5                                                  |
| **5** | **Mỗi nhóm có đúng một quỹ.** Lập nhóm là lập luôn quỹ                                                                                                                                 | Mọi chênh lệch giữa các thành viên được giải quyết **qua quỹ**. Không có nợ trực tiếp giữa hai thành viên, không cần chuyển tiền giữa các quỹ              |
| **6** | **Không phân loại nhóm.** Không có `type` (gia đình / bạn bè / ở ghép)                                                                                                                 | Mọi nhóm vận hành cùng một cơ chế. Khác biệt chỉ nằm ở cờ bật/tắt tính thừa thiếu                                                                          |
| **7** | **Dựng mới, không kế thừa schema đang chạy**                                                                                                                                           | Schema cũ theo mô hình "ví chung nằm trong bảng ví cá nhân", mâu thuẫn với quyết định 1. Việc gỡ ghi ở [STATE.md](../STATE.md) mục "Thiết kế lại 16–17/09" |

**Quy ước kỹ thuật:**

- **Tiền là số nguyên** đơn vị đồng (`BIGINT` / `Long`), luôn dương, tối đa `999.999.999.999`. Chiều tiền suy ra từ `type` và `money_source`.
- **Thời điểm dùng `TIMESTAMPTZ` / `Instant`**, kể cả lúc phát sinh giao dịch (`occurred_at`, tới giây). Lọc và báo cáo theo ngày thì đổi thời điểm sang lịch Việt Nam.
- **Giá trị liệt kê viết HOA** và được chặn bằng `CHECK` ở mức CSDL.
- **Xoá mềm:** nhóm, thành viên dùng `status`; quỹ đi theo nhóm; giao dịch dùng `deleted_at`.

---

# 2. Quy tắc bất biến <a id="2-quy-tac-bat-bien"></a>

Số thứ tự giữ cố định vì `api.md` dẫn chiếu theo số.

|   #    | Quy tắc                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Hậu quả nếu vi phạm                                                                     |
| :----: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------- |
| **1**  | Trong một khoản, `share_amount` **trống hết hoặc có hết**; có hết thì tổng = `amount`                                                                                                                                                                                                                                                                                                                                                                                                  | Tổng phần không khớp tiền thật, sai âm thầm                                             |
| **2**  | Người tham gia được chọn phải **có mặt trong nhóm tại thời điểm giao dịch**; có dòng thì ít nhất 1 người. Chọn đủ tất cả thì **không ghi dòng nào**                                                                                                                                                                                                                                                                                                                                    | Chia tiền cho người ngoài nhóm; "cả nhóm" có hai cách lưu                               |
| **3**  | Sửa giao dịch: **xoá hết dòng người tham gia cũ rồi ghi lại**, **hoàn tác ảnh hưởng cũ** lên quỹ rồi mới áp ảnh hưởng mới — tất cả trong một transaction CSDL                                                                                                                                                                                                                                                                                                                          | Số dư quỹ bị trừ oan                                                                    |
| **4**  | Rời rồi quay lại nhóm thì **tạo bản ghi thành viên mới**; bản ghi `LEFT` / `REMOVED` **không bao giờ sửa**                                                                                                                                                                                                                                                                                                                                                                             | Mọi khoản "cả nhóm" trong khoảng cũ tự đổi số                                           |
| **5**  | Chia đều lúc đọc phải **sắp người theo `user_id`**                                                                                                                                                                                                                                                                                                                                                                                                                                     | Mỗi màn hình lệch nhau 1đ                                                               |
| **6**  | Khoản chi nhóm **chỉ dùng danh mục hệ thống**, loại chi                                                                                                                                                                                                                                                                                                                                                                                                                                | Thành viên khác thấy danh mục trống, hoặc lộ danh mục riêng                             |
| **7**  | Thành viên `PENDING` **không thấy gì** của nhóm. Truy vấn quyền lọc **đích danh** `status = 'ACTIVE'`                                                                                                                                                                                                                                                                                                                                                                                  | Người chờ duyệt xem được dữ liệu nhóm                                                   |
| **8**  | **Mã lỗi phân quyền đúng bản chất:** nhóm không tồn tại → `404 GROUP_NOT_FOUND`; nhóm có thật nhưng không phải thành viên `ACTIVE` → `403 FORBIDDEN_NOT_GROUP_MEMBER`; thành viên thiếu vai trò → `403 FORBIDDEN_OWNER_REQUIRED` / `FORBIDDEN_TREASURER_REQUIRED`. **Ngoại lệ có chủ đích** của quy tắc "404 thay cho 403" trong `CLAUDE.md` (chốt 17/09/2026)                                                                                                                         | Người dùng nhận thông báo sai lý do                                                     |
| **9**  | Nhóm luôn có **đúng một** `OWNER` đang `ACTIVE`. Chủ nhóm muốn rời phải chuyển quyền trước                                                                                                                                                                                                                                                                                                                                                                                             | Nhóm không còn người quản lý                                                            |
| **10** | Quỹ luôn do **một thành viên `ACTIVE`** giữ (thủ quỹ)                                                                                                                                                                                                                                                                                                                                                                                                                                  | Quỹ trong tay người đã rời nhóm                                                         |
| **11** | `Σ phần mọi người = số dư quỹ`. Có test tự động kiểm tra sau mỗi thao tác ghi, sửa, xoá, xác nhận                                                                                                                                                                                                                                                                                                                                                                                      | Phép tính thừa thiếu sai mà không ai phát hiện                                          |
| **12** | Báo cáo chi tiêu và ngân sách nhóm **chỉ đọc `EXPENSE` đã xác nhận**                                                                                                                                                                                                                                                                                                                                                                                                                   | Tiền góp, tiền trả lại hoặc tiền lệch kiểm kê bị tính thành chi tiêu          |
| **13** | `ADJUSTMENT_*` và `REFUND` **chỉ thủ quỹ hoặc chủ nhóm** tạo được, và **luôn `CONFIRMED`**. `REFUND` cũng chỉ thủ quỹ hoặc chủ nhóm sửa được, sửa xong vẫn `CONFIRMED`. `ADJUSTMENT_*` chỉ sinh qua thao tác kiểm kê và không sửa                                                                                                                                                                                                                        | Thành viên bất kỳ tự lấy tiền quỹ hoặc sửa số dư                                        |
| **14** | **Mỗi nhóm đúng một quỹ**, tạo cùng transaction với nhóm. Quỹ chỉ đóng khi nhóm bị xoá                                                                                                                                                                                                                                                                                                                                                                                                 | Nhóm không có chỗ ghi tiền                                                              |
| **15** | **Cơ chế khóa kết hợp:** Khi kiểm kê hoặc xác nhận/hủy, **khóa bi quan dòng quỹ** (`SELECT FOR UPDATE` trên `group_funds`) rồi mới tính và **cộng/trừ chênh lệch** vào số dư (không ghi đè); kết hợp **khóa lạc quan trên giao dịch** (`@Version Long version` trên `group_transactions`) để ngăn duyệt/sửa trùng đồng thời                                                                 | Khoản ghi cùng lúc bị đè mất, sổ lệch số dư, hoặc 2 người cùng duyệt 1 khoản gây cập nhật số dư 2 lần |
| **16** | **Không** gợi ý thành viên chuyển tiền trực tiếp cho nhau. Chênh lệch chỉ giải quyết qua quỹ (góp thêm, quỹ trả lại tiền)                                                                                                                                                                                                                                                                                                                                                    | Tiền chuyển ngoài đời không vào sổ; khi giải tán, người có phần dương nhận tiền hai lần |
| **17** | **Mọi thành viên `ACTIVE` được ghi** `EXPENSE` và `CONTRIBUTION`. Người ghi không phải thủ quỹ / chủ nhóm → khoản ở **`PENDING`**; thủ quỹ hoặc chủ nhóm tự ghi → **`CONFIRMED` ngay**                                                                                                                                                                                                                                                                                                 | Người không cầm tiền tự làm thay đổi số dư quỹ                                          |
| **18** | **Quyền xác nhận / từ chối** mọi khoản: **thủ quỹ** hoặc **chủ nhóm**                                                                                                                                                                                                                                                                                                                                                                                                                  | Người không cầm tiền xác nhận việc tiền vào ra                                          |
| **19** | **Chỉ khoản `CONFIRMED` và chưa xoá** được tính vào số dư quỹ, phần của từng người và báo cáo. Số dư quỹ đổi **đúng lúc xác nhận**                                                                                                                                                                                                                                                                                                                                          | Khoản chưa ai kiểm chứng đã làm thay đổi tiền của nhóm                                  |
| **20** | **Sửa khoản đã `CONFIRMED`** bởi người không phải thủ quỹ / chủ nhóm → hoàn tác ảnh hưởng cũ và **quay về `PENDING`**. Thủ quỹ / chủ nhóm sửa → vẫn `CONFIRMED`, áp theo quy tắc 3                                                                                                                                                                                                                                                                                                     | Sửa số tiền sau khi đã xác nhận mà không ai kiểm lại                                    |
| **21** | **Chỉ chủ nhóm được xoá giao dịch** (mọi trạng thái). Xoá khoản `CONFIRMED` thì hoàn tác ảnh hưởng lên quỹ                                                                                                                                                                                                                                                                                                                                                                             | Thành viên xoá dấu vết khoản đã ghi                                                     |
| **22** | **Không ghi giao dịch hay kiểm kê ở tương lai:** `occurred_at` ≤ thời điểm hiện tại                                                                                                                                                                                                                                                                                                                                                                                                    | Số dư phản ánh tiền chưa hề phát sinh                                                   |
| **23** | **Xoá nhóm:** quỹ phải bằng 0 và không còn khoản `PENDING`; **nếu bật tính thừa thiếu** thì phần của mọi thành viên (kể cả thành viên đã rời nhóm) cũng phải bằng 0 (đã trả lại / nộp bù hết)                                                                                                                                                                                                                                                                                          | Nhóm biến mất khi tiền giữa các thành viên chưa giải quyết                              |
| **24** | **Nguồn tiền khớp loại giao dịch:** `EXPENSE` → `FUND` hoặc `PERSONAL`; `CONTRIBUTION` → `PERSONAL`; `REFUND`, `ADJUSTMENT_*` → `FUND`                                                                                                                                                                                                                                                                                                                                   | Không biết khoản đó có làm quỹ thay đổi không                                           |
| **25** | **Giới hạn tiền quỹ trả ra cho một người:** `REFUND` không được vượt **phần hiện có** (`net_balance`, số người đó còn trong quỹ) của người nhận, dù bật hay tắt tính thừa thiếu. Khi **sửa** `REFUND`, phép tính bỏ chính khoản đang sửa ra và chỉ kiểm khi số tiền tăng hoặc đổi người nhận                                                                                                                                                                                                                                                                                       | Trả nhiều hơn số người đó có trong quỹ, tức lấy tiền của người khác |
| **26** | **Nhóm `ARCHIVED` chỉ đọc.** Mọi thao tác ghi bị chặn, trừ mở lại và xoá nhóm. Chỉ lưu trữ được khi không còn khoản `PENDING`                                                                                                                                                                                                                                                                                                                                                          | Thay đổi dữ liệu của nhóm đã đóng sổ                                                    |
| **27** | **Bật tính thừa thiếu thì phải tất toán mới được rời** — áp dụng cho cả tự rời và bị mời rời: phần của người đó **bằng 0** và nhóm **không còn khoản `PENDING`**. Phần dương thì thủ quỹ trả ra (`REFUND`), phần âm thì người đó góp thêm                                                                                                                                                                                                                               | Người ở lại gánh nợ của người đã đi, hoặc tiền của người đã đi kẹt lại trong quỹ        |
| **28** | **Xử lý khi phần của người đã rời bị lệch:** Sau khi rời, nếu do sửa/xoá khoản cũ hoặc ghi bù làm phần của cựu thành viên $\ne 0$, bảng tiền (`/balances`) **hiển thị người đó** với trạng thái đã rời kèm số tiền thừa/thiếu; khi phần về 0 thì tự động ẩn. Giải quyết lệch bằng cả 2 cách: (A) người đó nộp bù thì ghi nhận `CONTRIBUTION`, (B) nhóm tự gánh thì sửa lại khoản cũ bỏ tích người đó khỏi người tham gia; nếu phần dương thì thủ quỹ hoàn trả (`REFUND`). | Tiền lệch của người đã đi bị bỏ quên, sổ nhóm không thể chốt sạch để xoá nhóm           |

---

# 3. Quy tắc theo từng đối tượng <a id="3-quy-tac-theo-tung-doi-tuong"></a>

## 3.1 Nhóm <a id="31-nhom"></a>

- **`target` là tổng tiền cả nhóm cần gom, chỉ để xem tiến độ.** Để trống = không đặt mục tiêu, không bao giờ đổi thành `0`; nhập thì phải lớn hơn 0. `target` **không** chia đầu người và không quyết định ai phải nộp bao nhiêu.
- **Cần nộp thêm chỉ theo số dư của từng người:** phần trong quỹ âm thì cần nộp đúng bằng `|phần trong quỹ|`, không âm thì `0`. Áp dụng cho mọi nhóm bật tính thừa thiếu, có hay không có `target`.
- **Không giới hạn số thành viên.** Người mới vào có phần trong quỹ = 0.
- **`is_join_without_confirm = false`**: người nhập mã nằm ở `PENDING`, chờ chủ nhóm duyệt.

**Cờ tính thừa thiếu (`is_settlement_enabled`):**

|                                    | Bật                                                           | Tắt (quỹ gia đình, người yêu…)                               |
| :--------------------------------- | :------------------------------------------------------------ | :----------------------------------------------------------- |
| Ý nghĩa                            | Theo dõi mỗi người đang có bao nhiêu trong quỹ                | **Không ai cần trả ai**                                      |
| Bảng tiền từng người (`/balances`) | Trả tiền túi, đã được hoàn, phải chịu, phần trong quỹ, cần nộp thêm | Cùng các trường như khi bật, chỉ `total_needed_contribution` cấp nhóm là `null`. Ứng dụng chỉ hiện mục tiêu, tổng tiền quỹ đang có và số còn lại của mỗi người trong quỹ (`net_balance`), ẩn phần quyết toán |
| Người tham gia của khoản chi       | Được ghi và dùng để tính                                      | **Vẫn được ghi**, để bật lại sau vẫn tính được khoản cũ      |
| Quỹ trả tiền cho thành viên (`REFUND`) | Không vượt phần của người nhận                            | Không vượt phần của người nhận                               |
| Điều kiện xoá nhóm                 | Quỹ = 0 **và** phần mọi người = 0                             | Quỹ = 0                                                      |
| Điều kiện rời nhóm (quy tắc 27)    | Phần người rời = 0 **và** không còn khoản chờ                 | Rời tự do                                                    |

## 3.2 Thành viên <a id="32-thanh-vien"></a>

| Quy tắc                                                                                                                                    | Lý do                                                                                                                                                                      |
| :----------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Rời rồi quay lại → **bản ghi mới**                                                                                                         | Giữ đúng lịch sử ai có mặt lúc nào (quy tắc 4)                                                                                                                             |
| **Từ chối người `PENDING` = xoá hẳn bản ghi**                                                                                              | Người đó chưa từng ở trong nhóm; `ck_gm_dates` không cho bản ghi `REMOVED` thiếu `joined_at`                                                                               |
| **Rời nhóm → hệ thống tự `REJECTED`** mọi khoản `PENDING` có `created_by` = người rời. Khoản `REJECTED` sẵn không cần xử lý thêm           | Người đã rời không còn truy cập nhóm để sửa lại; dọn khoản treo cho thủ quỹ                                                                                                |
| **Vai trò không gán tuỳ ý.** Chỉ đổi qua **chuyển quyền chủ nhóm**: chủ cũ thành `MEMBER`, người nhận thành `OWNER`, cùng một transaction  | Luôn giữ đúng một `OWNER` (quy tắc 9)                                                                                                                                      |
| Chủ nhóm không tự rời, không tự mời mình rời                                                                                               | Như trên                                                                                                                                                                   |
| **Bật tính thừa thiếu: tất toán rồi mới rời**, kể cả khi bị chủ nhóm mời rời (quy tắc 27). Người còn nợ mà không góp thì không mời ra được | Không để người ở lại gánh nợ                                                                                                                                               |
| **Có mặt tính theo thời điểm, tới giây:** từ lúc vào (`joined_at`) tới **trước** lúc rời (`left_at`) ([pipeline.md](pipeline.md) mục 1)    | Rời lúc nào thì từ ngay sau lúc đó không bị chia nữa. Mọi khoản đã ghi đều có thời điểm trước lúc rời (không cho ghi tương lai), nên phần đã kiểm tra lúc rời không tự đổi |
| **Thủ quỹ không phải một vai trò** của thành viên                                                                                          | Người giữ tiền lưu ở quỹ (`keepper_id`)                                                                                                                                    |
| **Người đã rời bị lệch phần sau khi rời**                                                                                                  | Hiển thị trên bảng `/balances` nếu phần $\ne 0$; về 0 tự động ẩn. Cho phép nộp bù (`CONTRIBUTION`) hoặc nhóm tự gánh bằng cách sửa bỏ tích người đó (quy tắc 28)           | Minh bạch kế toán, nhóm không bị kẹt khi cần chốt sổ |

## 3.3 Quỹ <a id="33-quy"></a>

| Quy tắc                                                                                                                                                            | Lý do                                                        |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------- |
| **Đúng một quỹ mỗi nhóm**, tạo cùng nhóm                                                                                                                           | Quyết định 5                                                 |
| **Không có số dư ban đầu**, quỹ bắt đầu từ 0. Tiền có sẵn ghi thành khoản góp của đúng người đã đưa                                                                | Mọi đồng trong quỹ đều thuộc phần của một người (quy tắc 11) |
| Người giữ quỹ phải là thành viên `ACTIVE`; chủ nhóm đổi được người giữ                                                                                             | Quy tắc 10                                                   |
| **Thủ quỹ phải bàn giao quỹ** (`PUT /v1/groups/{id}/fund-kepper`) cho thành viên `ACTIVE` khác **trước khi rời** hoặc bị mời rời. Chủ nhóm muốn mời thủ quỹ rời thì bàn giao quỹ trước | Người giữ tiền phải bàn giao tường minh, không chuyển ngầm   |
| Quỹ **không đóng riêng được**; vòng đời quỹ đi theo trạng thái của nhóm (không có trường `status`)                                                                   | Quy tắc 14                                                   |
| **Được phép âm**                                                                                                                                                   | Nhóm chi vượt quỹ là chuyện có thật                          |

## 3.4 Giao dịch <a id="34-giao-dich"></a>

| `type`            | Khi nào                                                         | `money_source` | Quỹ | Người tham gia   | `transactor_id` là | Ai tạo được                         | Vào báo cáo chi tiêu |
| :---------------- | :-------------------------------------------------------------- | :------------- | :-: | :--------------- | :----------- | :---------------------------------- | :------------------: |
| `EXPENSE`         | Nhóm tiêu tiền quỹ                                              | `FUND`         |  −  | Vắng = cả nhóm   | Người trả    | Mọi thành viên                      |          ✅          |
| `EXPENSE`         | Một người trả hộ bằng tiền túi                                  | `PERSONAL`     |  0  | Vắng = cả nhóm   | Người trả    | Mọi thành viên                      |          ✅          |
| `CONTRIBUTION`    | Thành viên góp tiền vào quỹ                                     | `PERSONAL`     |  +  | Không bao giờ có | Người góp    | Mọi thành viên                      |          ❌          |
| `REFUND`          | Quỹ **trả tiền cho thành viên**: hoàn tiền túi, trả lại tiền đã góp (góp dư, rời nhóm, giải tán) | `FUND`         |  −  | Không bao giờ có | Người nhận   | **Thủ quỹ, chủ nhóm**               |          ❌          |
| `ADJUSTMENT_UP`   | Kiểm kê: tiền thật **nhiều hơn** sổ                             | `FUND`         |  +  | Vắng = cả nhóm   | Thủ quỹ      | **Thủ quỹ, chủ nhóm** (qua kiểm kê) |          ❌          |
| `ADJUSTMENT_DOWN` | Kiểm kê: tiền thật **ít hơn** sổ                                | `FUND`         |  −  | Vắng = cả nhóm   | Thủ quỹ      | **Thủ quỹ, chủ nhóm** (qua kiểm kê) |          ❌          |

**Trạng thái xác nhận:**

| Trạng thái  | Nghĩa                                       | Tính vào tiền của nhóm |
| :---------- | :------------------------------------------ | :--------------------: |
| `PENDING`   | Đã ghi, chờ thủ quỹ / chủ nhóm xác nhận     |           ❌           |
| `CONFIRMED` | Đã xác nhận, hoặc thủ quỹ / chủ nhóm tự ghi |           ✅           |
| `REJECTED`  | Bị từ chối                                  |           ❌           |

| Chuyển                               | Ai làm                                                  |
| :----------------------------------- | :------------------------------------------------------ |
| `PENDING` → `CONFIRMED` / `REJECTED` | Thủ quỹ hoặc chủ nhóm (quy tắc 18)                      |
| `CONFIRMED` → `PENDING`              | Tự động khi thành viên thường sửa khoản đó (quy tắc 20) |
| `REJECTED` → `PENDING`               | Tự động khi người ghi sửa lại khoản bị từ chối          |
| Xoá                                  | Chỉ chủ nhóm (quy tắc 21)                               |

- **Ai sửa được:**
  - `EXPENSE`, `CONTRIBUTION`: **người ghi** sửa khoản mình ghi; **chủ nhóm** sửa mọi khoản.
  - `REFUND`: chỉ **thủ quỹ hiện tại** hoặc **chủ nhóm**, không đổi loại. Sửa xong vẫn `CONFIRMED`. Người từng ghi mà nay không giữ quỹ thì không sửa được.
  - `ADJUSTMENT_*`: không sửa — kiểm kê sai thì kiểm kê lại.
- **Người trả / người góp / người nhận** phải có mặt trong nhóm tại thời điểm giao dịch.
- **Người bấm ghi** (`created_by`) có thể khác người trả, ví dụ thủ quỹ ghi hộ.
- **Mượn quỹ đi việc riêng** ghi bằng `EXPENSE` (`FUND`), chỉ chọn những người đi; trả lại bằng `CONTRIBUTION`. Không có loại `LOAN` riêng.
- **Quỹ trả tiền cho thành viên** (hoàn tiền túi cho người đã trả hộ, hoặc trả lại tiền người đó đã góp) đều ghi bằng `REFUND`: lúc đó quỹ mới bị trừ và phần trong quỹ của người nhận giảm tương ứng. Không còn loại riêng cho "rút tiền góp".

**Vì sao kiểm kê có loại riêng:** sửa thẳng số dư phá công thức _số dư = Σ giao dịch_ và mất dấu vết; ghi thành `EXPENSE` thì phải gán danh mục cho một khoản không rõ tiêu vào đâu. Tiền luôn dương nên chiều lệch nằm ở `type`.

## 3.5 Người tham gia <a id="35-nguoi-tham-gia"></a>

| Trường hợp                          | Bản ghi người tham gia                        |
| :---------------------------------- | :-------------------------------------------- |
| Khoản chi chung cả nhóm             | **Không có dòng nào**                         |
| B và C đi chơi riêng                | 2 dòng: B, C — `share_amount` trống, chia đều |
| Chia không đều (A 40%, B 60%)       | 2 dòng, **có** `share_amount`                 |
| Kiểm kê, chia đều cho cả nhóm       | Không có dòng nào                             |
| Góp quỹ, quỹ trả lại tiền           | Không bao giờ có dòng                         |

- Người dùng tích đủ cả nhóm thì **lưu thành rỗng**, để một ý nghĩa chỉ có một cách lưu.
- Khi đổi thời điểm của giao dịch, kiểm tra lại người trả và người tham gia với thời điểm mới.

## 3.6 Tham chiếu sang module khác <a id="36-tham-chieu"></a>

- Khoản chi nhóm **chỉ dùng danh mục hệ thống** (`categories.user_id IS NULL`), và phải là danh mục **loại chi**.
- Phân hệ nhóm **không** có khoá ngoại sang `wallets`, `transactions` của sổ cá nhân (quyết định 4).

## 3.7 Lưu trữ nhóm <a id="37-luu-tru-nhom"></a>

|                       |                                                                                                                                        |
| :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------- |
| **Ai làm**            | Chỉ chủ nhóm: lưu trữ và mở lại                                                                                                        |
| **Điều kiện lưu trữ** | Không còn khoản `PENDING`                                                                                                              |
| **Nhóm đang lưu trữ** | Chỉ xem: chi tiết nhóm, quỹ, thành viên, giao dịch, bảng tiền từng người                                                               |
| **Bị chặn**           | Ghi / sửa / xác nhận / xoá giao dịch, kiểm kê, đổi thông tin nhóm và quỹ, lấy mã mời, vào nhóm, duyệt, mời rời, rời nhóm, chuyển quyền |
| **Vẫn làm được**      | Mở lại nhóm; xoá nhóm (theo quy tắc 23)                                                                                                |

---

# 4. Đã chốt <a id="4-da-chot"></a>

- Không làm nhóm không quỹ, **không cần `group_settlements`**.
- Không phân loại nhóm.
- Dựng mới thay cho schema nhóm đang chạy.
- Mượn quỹ đi việc riêng: dùng `EXPENSE` + người tham gia, trả lại bằng `CONTRIBUTION`; **không có `LOAN`**.
- Khoản kiểm kê lưu `transactor_id` = **thủ quỹ đang giữ quỹ**, `created_by` = người bấm kiểm kê.
- Người ngoài nhóm nhận `403`, không phải `404` (quy tắc 8).
- Không đặt mục tiêu thì `target` là `NULL`.
- **Mục tiêu = tổng tiền cần gom, chỉ để xem tiến độ** (17/09). Cần nộp thêm không chia theo mục tiêu mà chỉ theo số dư âm của từng người (chốt lại 29/09).
- **Mọi thành viên được ghi, chỉ thủ quỹ và chủ nhóm xác nhận**; trạng thái `PENDING` / `CONFIRMED` / `REJECTED`; khoản chờ chưa tính vào tiền; người có quyền tự ghi thì xác nhận luôn; sửa khoản đã xác nhận thì quay về chờ (17/09).
- **Chỉ chủ nhóm được xoá giao dịch** (17/09).
- **Không giới hạn số thành viên; không cho ghi ngày tương lai** (17/09).
- **Giao dịch lưu thời điểm tới giây** (`occurred_at`) thay cho ngày; có mặt tính từ lúc vào tới trước lúc rời (17/09).
- **Mỗi nhóm đúng một quỹ** — không chuyển tiền giữa các quỹ, không đóng quỹ riêng (17/09).
- **Nguồn tiền `money_source`** (`FUND` / `PERSONAL`) thay cho `group_fund_id`; giao dịch vẫn gắn nhóm qua `group_id` (17/09).
- **Loại `REFUND` (quỹ trả tiền cho thành viên):** chỉ thủ quỹ và chủ nhóm ghi **và sửa**, luôn đã xác nhận; lúc đó quỹ mới bị trừ (17/09).
- **Tắt tính thừa thiếu = không ai cần trả ai.** Bảng tiền hiện mục tiêu, tổng tiền quỹ và số còn lại của mỗi người trong quỹ; xoá nhóm chỉ cần quỹ = 0 (17/09, sửa 29/09).
- **Giữ `ARCHIVED`:** chỉ xem, chủ nhóm mở lại được (17/09).
- **Xoá nhóm phải trả hết tiền cho mọi người trước** nếu bật tính thừa thiếu (17/09).
- **Giữ giới hạn trả lại:** `REFUND` không vượt phần của người nhận (quy tắc 25), dù bật hay tắt tính thừa thiếu (chốt lại 29/09). Muốn cho mượn thì ghi `EXPENSE` chỉ chọn người mượn (17/09).
- **Bỏ số dư ban đầu của quỹ:** quỹ bắt đầu từ 0, tiền có sẵn ghi thành khoản góp của đúng người (17/09).
- **Rời nhóm phải tất toán về 0** nếu bật tính thừa thiếu, cả tự rời lẫn bị mời rời (quy tắc 27, 17/09).
- **Bỏ loại `WITHDRAWAL` và khái niệm "số đã góp" (29/09):** nhóm chỉ cần biết mỗi người còn bao nhiêu trong quỹ. `REFUND` gánh cả hai việc: hoàn tiền túi và trả lại tiền đã góp. Trường `total_contributed`, `total_withdrawn` bỏ khỏi `/balances`.
- **Đổi tên cờ `is_split_equally` thành `is_settlement_enabled`** — nghĩa thật là bật / tắt tính thừa thiếu (17/09).
- **Mã mời 8 ký tự**, chữ hoa và số dễ đọc; **không thời hạn hết hạn** (có mã là tham gia được).
- **`Idempotency-Key` giữ mức khuyến nghị**, không bắt buộc: khoản thành viên ghi trùng vẫn phải qua bước xác nhận của thủ quỹ / chủ nhóm (17/09).
- **Xử lý phần người đã rời bị lệch:** hiển thị trên bảng tiền khi $\ne 0$ (về 0 tự động ẩn); hỗ trợ cả 2 cách giải quyết: người đó góp bù (`CONTRIBUTION`) hoặc nhóm tự gánh (bỏ tích người đó khi sửa khoản cũ); xoá nhóm đòi hỏi phần của mọi người (kể cả người đã rời) đều bằng 0 (quy tắc 28, 17/09).
- **Rời nhóm → tự `REJECTED` khoản `PENDING`** do người đó ghi (`created_by`). Khoản `REJECTED` sẵn không cần xử lý thêm (17/09).
- **Thủ quỹ phải bàn giao quỹ trước khi rời**, không tự chuyển ngầm về chủ nhóm. Chủ nhóm muốn mời thủ quỹ rời thì bàn giao quỹ trước (17/09).
- **Xác nhận / từ chối hàng loạt** khoản `PENDING` trong một lần gọi API (17/09).
- **Kỳ của `GET /summary`:** dùng tham số `month=YYYY-MM`, mặc định là tháng hiện tại theo giờ Việt Nam UTC+7 (17/09).

---

# 5. Chưa chốt và để sau <a id="5-chua-chot-va-de-sau"></a>

| Hạng mục                            | Trạng thái | Ghi chú                                                                                                                |
| :---------------------------------- | :--------- | :--------------------------------------------------------------------------------------------------------------------- |
| **Đồng bộ sang ví cá nhân**         | Để sau     | Cần `counts_in_report` cho giao dịch cá nhân, bảng liên kết `group_personal_links`, và khoá sửa số tiền ở phía cá nhân |
| **Ngân sách nhóm, hoá đơn định kỳ** | Để sau     | Cần hai thực thể riêng                                                                                                 |
| **Mời qua email**                   | Để sau     | Cần bảng `group_invitations`                                                                                           |
| **Bình luận theo giao dịch**        | Để sau     | Bảng `group_transaction_comments`                                                                                      |
| **Rate limit ghi khoản**            | Để sau     | Giới hạn tần suất ghi khoản để tránh spam `PENDING`                                                                    |

## 5.1 Để họp sau <a id="51-de-hop-sau"></a>

| Hạng mục                                                                           | Ghi chú                                                                                                                                                                                                                                                     |
| :--------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Tài khoản bị khoá hoặc xoá** khi đang là chủ nhóm, thủ quỹ, hoặc còn phần khác 0 | **Không ảnh hưởng CSDL**: người dùng chỉ xoá mềm, khoá ngoại `RESTRICT`. Đề xuất đang có: xoá tài khoản xử lý như rời nhóm (phải chuyển quyền chủ nhóm và tất toán về 0 ở mọi nhóm bật tính thừa thiếu); bị khoá thì vẫn là thành viên, không đổi gì ở nhóm |
