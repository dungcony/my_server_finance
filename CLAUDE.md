# CLAUDE.md — Backend

Hướng dẫn cho Claude Code khi làm việc trong `source/server/` — backend Spring Boot của dự án Quản lý tài chính cá nhân có AI.

**Đọc trước:** [CLAUDE.md gốc](../../CLAUDE.md) ở thư mục DATN — chứa 3 nguyên tắc bất biến, quy tắc nghiệp vụ, bản đồ tài liệu (`api/*.md`, `THIET-KE-CSDL.md`) áp dụng cho toàn dự án bao gồm cả backend này.

## Dự án GSD

Project này được khởi tạo và quản lý qua GSD (Get Shit Done):

| Cần biết gì | Đọc file |
|---|---|
| Bối cảnh, requirements, quyết định | [.planning/PROJECT.md](.planning/PROJECT.md) |
| Danh sách 80 requirements (13 nhóm) | [.planning/REQUIREMENTS.md](.planning/REQUIREMENTS.md) |
| 5 phase thực thi, success criteria | [.planning/ROADMAP.md](.planning/ROADMAP.md) |
| Đang ở đâu, việc tiếp theo | [.planning/STATE.md](.planning/STATE.md) |
| Research stack Spring Boot | [.planning/research/SUMMARY.md](.planning/research/SUMMARY.md) |

**Bắt đầu phiên làm việc mới:** đọc STATE.md trước để biết đang ở phase nào. Dùng `/gsd-plan-phase <N>` để lập kế hoạch chi tiết cho một phase, `/gsd-progress` để xem tiến độ tổng thể.

## Ngăn xếp công nghệ đã chốt

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 17 |
| Framework | Spring Boot 3.3.x/3.4.x |
| Build tool | Maven |
| CSDL | PostgreSQL 14+ |
| Migration | Flyway — **dùng chung `db/migration/` ở thư mục gốc DATN, không copy vào đây** |
| Data access | Spring Data JPA (chủ đạo) + native SQL/JdbcTemplate cho các điểm nóng (atomic balance update, `fn_category_tree`, view `v_budget_progress`) |
| Auth | JWT qua `jjwt` — access token stateless 1h, refresh token băm SHA-256 lưu DB 30 ngày, rotation + reuse detection |
| Idempotency | Bảng DB `idempotency_keys`, không dùng Redis |
| Rate limit | Bucket4j in-memory + Caffeine |
| Job nền | `@Scheduled` với `zone="Asia/Ho_Chi_Minh"`, không dùng Quartz |
| Test | Testcontainers PostgreSQL cho integration test — **không dùng H2** (H2 không hỗ trợ constraint/hàm SQL của schema V1-V5) |

Chi tiết lý do từng lựa chọn: [.planning/research/SUMMARY.md](.planning/research/SUMMARY.md).

## Cấu trúc package

**Package-by-feature**, không phải package-by-layer:

```
com.datn.financeapp
├── common/          # response wrapper, exception handler, security, idempotency, rate limit
├── auth/            # api/01-XAC-THUC
├── wallet/          # api/02-VI
├── category/        # api/03-DANH-MUC
├── transaction/      # api/04-GIAO-DICH — module lõi, rủi ro cao nhất
├── budget/          # api/05-NGAN-SACH
├── report/          # api/06-BAO-CAO
├── ai/              # api/07-AI
├── debt/            # api/08-SO-NO
├── recurring/       # api/09 phần A
├── goal/            # api/09 phần B
├── group/           # api/10-NHOM-GIA-DINH
└── scheduler/       # các @Scheduled job
```

Mỗi package nghiệp vụ tự chứa Controller/Service/Repository/dto/entity của mình. Không tạo `controller/`, `service/`, `repository/` ở cấp cao nhất.

### DTO — tách hai chiều vào/ra

```
<module>/dto/
├── request/     # dữ liệu client gửi lên
└── response/    # dữ liệu trả về client
```

**Quy ước đặt tên — theo được thì phân loại thư mục là hiển nhiên:**

| Loại | Mẫu tên | Ví dụ |
|---|---|---|
| Vào | `<Hành động><Domain>Request` | `CreateWalletRequest`, `UpdateBudgetRequest` |
| Vào (lọc/tìm) | `<Domain>FilterRequest` | `TransactionFilterRequest` |
| Ra — một bản ghi | `<Domain>Response` | `WalletResponse` |
| Ra — bản ghi đầy đủ | `<Domain>DetailResponse` | `CategoryDetailResponse` |
| Ra — một dòng trong danh sách | `<Domain>ListItemResponse` | `DebtListItemResponse` |
| Ra — cả trang danh sách | `<Domain>ListResponse` | `TransactionListResponse` |

**Không đặt hậu tố `Dto`** — nhìn tên phải biết ngay chiều dữ liệu. Sáu file từng đặt kiểu đó
(`UserDetailDto`, `WalletStatsDto`…) đã đổi hết ở nhóm F của PRD 02.

⚠️ **Đổi tên class KHÔNG đổi khoá JSON** — khoá đến từ `@JsonProperty` và cấu hình `snake_case`
toàn cục, độc lập hoàn toàn với tên class Java. Nhưng phải kiểm bằng test canh `jsonPath` chứ
đừng tin suông.

## Quy tắc bắt buộc riêng cho backend này

Kế thừa toàn bộ 8 quy tắc nghiệp vụ bất biến ở CLAUDE.md gốc, cộng thêm:

0. **Đối chiếu với schema thật trước khi tin vào tài liệu.** `api/*.md` và `db/migration/V*.sql` là hai tài liệu được viết song song và **không bao giờ gặp nhau ở runtime** — nên chúng lệch nhau âm thầm, không có gì báo lỗi. App Flutter không dính vấn đề này vì compiler ép buộc; backend thì không có cơ chế tương đương. Trước khi lập kế hoạch hay code một phase, **mở `db/migration/V*.sql` đọc bảng/cột/trigger thật**, đừng tin mô tả trong `api/` hay `.planning/`.
   Đã có bốn lỗi được tìm ra đúng theo cách này: `v_budget_progress` thiếu điều kiện quyền, `groups` thiếu cột hạn mã mời mà `api/10` yêu cầu, `transactions.date` là `DATE` chứ không phải `TIMESTAMPTZ`, và 4 bảng hạ tầng xác thực chưa tồn tại.
1. **Không tự bịa API hoặc trường dữ liệu chưa có trong `api/*.md`.** Nếu thiếu/mâu thuẫn, dừng lại hỏi thay vì đoán.
2. **Không viết trigger CSDL cho số dư ví** (`wallets.current_balance`) — logic này nằm ở tầng Service, gói trong `@Transactional`.
   **Nhưng schema đã có sẵn 6 trigger**, trong đó `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) **sở hữu** `debts.paid_amount`, `debts.status`, `savings_goals.saved_amount`, `savings_goals.status` — backend chỉ chèn/xoá bản ghi `debt_payments`/`goal_contributions`, **tuyệt đối không ghi bốn cột đó**. Bảng đầy đủ ở [db/README.md](../../db/README.md) mục "Trigger có sẵn".
3. **Cập nhật `current_balance` phải atomic** (`UPDATE ... SET balance = balance + :delta`), không load-modify-save qua entity — tránh lost-update, đặc biệt với ví chung nhóm gia đình.
4. **Sửa/xoá giao dịch phải đúng 3 bước** trong cùng 1 `@Transactional`: hoàn tác ảnh hưởng cũ → ghi giá trị mới → áp dụng ảnh hưởng mới. Tránh transaction self-invocation (gọi method `@Transactional` từ trong cùng class) làm mất annotation.
5. **Mọi truy vấn ví/giao dịch/ngân sách/nợ/mục tiêu phải kiểm tra quyền ngay trong JPQL/native query**, không load hết rồi lọc ở code Java. Không có quyền → 404.
6. **Mọi thống kê/lọc theo danh mục cha phải cộng gộp danh mục con** — dùng lại một repository method gọi `fn_category_tree`, không lặp điều kiện lọc ở nhiều nơi.
7. **Mọi báo cáo phải loại `type='transfer'`.** Gom nhóm báo cáo theo ngày/tháng dùng thẳng cột `transactions.date` (kiểu `DATE` thuần, không có thành phần giờ) — KHÔNG chuyển đổi múi giờ dưới bất kỳ hình thức nào. Đây là thiết kế cố ý để tránh hẳn lớp bug múi giờ.
8. **`amount` luôn kiểu `Long`**, không bao giờ `Double`/`float` ở bất kỳ tầng nào.
9. **AI (parse-text/OCR) không bao giờ tự tạo `transactions`** — luôn qua `ai_drafts.status='pending'`, chờ duyệt; khi duyệt phải ghi `user_corrections`.
10. **Mã lỗi chỉ khai trong `ErrorCode` enum**, không bao giờ là chuỗi rời. `BusinessException` chỉ nhận `ErrorCode` — constructor nhận `String` đã bị gỡ, và đừng thêm lại: chính nó là thứ cho phép gõ sai mã mà build vẫn qua.
    - Ném lỗi: `throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);`
    - Cần câu cụ thể hơn câu mặc định: `new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.")`
    - **Tên hằng số chính là mã đi ra JSON** — đổi tên là đổi hợp đồng với app Flutter (`lib/core/network/api_error.dart`), phải sửa `api/*.md` và app trong cùng lần thay đổi.
    - **`NOT_FOUND` cố ý dùng chung cho mọi loại tài nguyên** (46 chỗ) — xem quy tắc 5: không có quyền thì trả 404 để không lộ bản ghi có tồn tại hay không. Tách thành `WALLET_NOT_FOUND`, `BUDGET_NOT_FOUND`… là làm hỏng chính điều đó. Chỉ `message` mới nói cụ thể, vì nó chỉ hiện cho người có quyền hợp lệ.
11. **Module này cần dữ liệu module kia thì gọi qua Service của nó, không đụng `Repository`/`Entity`.** Chi tiết và hiện trạng ở mục ["Ranh giới giữa các module"](#ranh-giới-giữa-các-module) ngay dưới.
12. **Service viết thẳng thành class, KHÔNG tách interface — trừ khi đã có từ 2 implementation trở lên.** Chi tiết ở mục ["Khi nào Service cần interface"](#khi-nào-service-cần-interface).

## Khi nào Service cần interface

**Mặc định: viết thẳng thành class, không tách interface.** Chỉ tách khi **đã thực sự có từ hai
implementation trở lên** — không phải khi phỏng đoán rằng sau này có thể có.

### Hai interface đang có, và vì sao chúng xứng đáng

| Interface | Các implementation | Tráo lúc nào |
|---|---|---|
| `PasswordResetNotifier` | `LogPasswordResetNotifier` · `SmtpPasswordResetNotifier` — **hai class thật**, chọn bằng `@Profile` | Test in mã ra log, chạy thật gửi Gmail |
| `GoogleIdTokenVerifier` | `GoogleApiIdTokenVerifier` + mock do `@MockitoBean` sinh trong test | Test không gọi ra máy chủ Google |

Điểm chung: **có thứ hai để tráo vào, và việc tráo xảy ra thật ở mỗi lần chạy test.** Không có
interface thì test auth sẽ đi gửi mail thật và gọi API Google thật — chậm, phụ thuộc mạng, và
không có cách nào sinh ra `id_token` hợp lệ để thử.

Riêng `GoogleIdTokenVerifier` đáng chú ý: Mockito mock được cả class cụ thể, nên interface ở đây
**không bắt buộc** về mặt kỹ thuật. Nó xứng đáng vì lý do khác — bản thật tải khoá công khai từ
Google lúc khởi tạo, tách interface làm ranh giới "ra mạng ngoài" thành hiển nhiên khi đọc code.

### Vì sao 15 service còn lại không tách

Không cái nào có implementation thứ hai. Thêm interface cho chúng nghĩa là mỗi service có thêm
một file chép lại y nguyên chữ ký của class ngay bên cạnh, đổi lại:

- sửa một method phải sửa hai chỗ;
- "go to definition" nhảy vào interface rỗng thay vì code thật.

**Ràng buộc kỹ thuật cũ đã hết hiệu lực.** Spring đời cũ dùng JDK dynamic proxy nên `@Transactional`
bắt buộc phải có interface mới chạy. Từ Spring Boot 2.x, mặc định là CGLIB proxy thẳng class —
annotation hoạt động bình thường trên class không interface. Nhiều tài liệu vẫn dạy theo bản cũ,
đó là lý do quy ước này còn phổ biến.

Mock trong test cũng **không** phải lý do: test của dự án là integration test trên Testcontainers
PostgreSQL thật, và Mockito mock được class cụ thể từ lâu.

### Khi nào thì tách

Khi cái thứ hai **xuất hiện thật**, không phải khi tưởng tượng nó sẽ xuất hiện. Ví dụ đủ điều kiện:

- Thanh toán qua nhiều cổng (VNPay / Momo) cùng một hợp đồng gọi
- Một bản cài đặt thật + một bản giả cho test, như hai interface ở trên
- Cần đảo ngược phụ thuộc để cắt vòng tròn giữa hai module

Lúc đó IDE trích interface ra mất khoảng 30 giây (Refactor → Extract Interface). Đây **không** phải
quyết định phải làm sớm để tránh trả giá về sau — chi phí như nhau ở mọi thời điểm, nên cứ đợi tới
lúc thật sự cần.

> **Muốn nhìn nhanh một service có những hàm gì thì dùng IDE, đừng viết interface.** IntelliJ:
> `Ctrl+F12` (Structure). VS Code: Outline. Cách này luôn đúng và không tốn file nào — trong khi
> interface viết ra để "cho dễ đọc" sẽ lệch với class ngay lần sửa đầu tiên mà không ai nhận ra.
> Muốn tách phần công khai khỏi phần nội bộ thì để method nội bộ là `private` — đó mới là công cụ
> đúng cho việc đó.

## Ranh giới giữa các module

Thư mục đã chia đúng package-by-feature từ đầu, nhưng **ranh giới chưa được tôn trọng ở tầng code**
— nhiều service vẫn import thẳng `Repository`/`Entity` của module khác, biến nó thành
package-by-layer trá hình.

### Luật cho code mới — không có ngoại lệ

| | Việc |
|---|---|
| ✅ | Cần dữ liệu module khác → gọi **Service** của module đó, nhận về **DTO** |
| ✅ | Liên kết xuyên module dùng **ID thuần** (`UUID categoryId`), không `@ManyToOne` |
| ❌ | `import com.datn.financeapp.<module khác>.repository.*` |
| ❌ | `import com.datn.financeapp.<module khác>.entity.*` |
| ❌ | Trả `Entity` ra khỏi module — kể cả cho service khác |

**Vì sao không dùng `@ManyToOne` xuyên module:** tránh ghép chặt ở tầng JPA, tránh
`LazyInitializationException` khi entity bị dùng ngoài transaction gốc, và ép mọi truy cập đi qua
Service — nơi duy nhất kiểm được quyền.

⚠️ **Bọc lại qua Service thì đừng biến 1 truy vấn thành N+1.** Nhiều chỗ hiện tại đang nạp theo lô
hoặc join trong SQL. Đổi máy móc sang "gọi service từng bản ghi" là biến báo cáo 1 truy vấn thành
500. Cần dữ liệu theo lô thì thêm method nhận `Collection<UUID>` trả `Map<UUID, XxxResponse>`.

⚠️ **Điều kiện quyền phải đi cùng, nằm nguyên trong SQL** (quy tắc 5). Bọc lại không được biến
thành "lấy hết rồi lọc ở Java".

### Chiều phụ thuộc

```
tầng nền:     category · wallet · notification        (không phụ thuộc module nghiệp vụ nào)
tầng giữa:    transaction                             (dùng category, wallet)
tầng trên:    budget · debt · goal · recurring · report · scheduler
```

Tầng trên gọi xuống tầng dưới là bình thường. Ngược lại thì **dừng lại nghĩ trước** — thường là
đặt logic sai chỗ, và cách đúng là phát sự kiện (xem `transaction/event/`) để module quan tâm tự
lắng nghe, thay vì gọi thẳng.

**Nhưng "gọi ngược" không phải lúc nào cũng sai.** `TransactionService` đọc dữ liệu `budget` để trả
`affected_budgets` — ghi một khoản chi xong thì app cần biết ngay nó ăn vào ngân sách nào
(`api/04` mục 4). Đây là câu trả lời **đồng bộ** trong cùng một response, sự kiện bất đồng bộ
không thay được. Chiều ngược lại — budget cần biết có giao dịch mới để cảnh báo vượt hạn mức —
thì đúng là dùng sự kiện (`BudgetAlertListener`).

Nguyên tắc phân biệt: **người dùng cần thấy kết quả ngay trong response này** thì gọi thẳng qua
Service; **chỉ là hệ quả phụ** (gửi thông báo, cập nhật thống kê) thì phát sự kiện.

### Hiện trạng — 7 module còn nợ, đang chờ dọn

Đã dọn xong: **`auth`** (nhóm G, PRD 02) · `wallet` · `category` · `notification` vốn đã sạch.

Còn vi phạm — **7 module**, 41 kiểu import lậu khác nhau (57 dòng `import` nếu đếm cả lặp lại giữa các file):

| Module | Đang đụng thẳng vào |
|---|---|
| `transaction` | `category`, `wallet`, `budget` |
| `budget` | `category`, `wallet`, `notification` |
| `report` | `category`, `wallet`, `transaction` |
| `debt` | `category`, `wallet`, `notification`, `transaction` |
| `recurring` | `category`, `wallet`, `transaction` |
| `goal` | `category`, `wallet` |
| `scheduler` | `report` |

Dọn nốt là **nhóm H của [PRD 02](../../prd/02-CHUAN-HOA-KIEN-TRUC-BACKEND/PRD.md)**, cố ý hoãn tới
khi app đuổi kịp nhóm API 08/09/11 — nó xuyên qua `transaction`, module rủi ro cao nhất, và test
hiện chưa đủ dày để bảo vệ một cuộc refactor quy mô đó.

> **Đang sửa code trong 7 module đó thì làm gì?** Đừng thêm import lậu mới, kể cả khi file đó đã
> có sẵn vài cái. Cần dữ liệu module khác thì thêm method vào Service của module kia và gọi qua
> đó — làm dần theo nhu cầu, mỗi lần một ít, thay vì chờ một đợt refactor lớn.

## Ngôn ngữ

Giao tiếp, commit message: **tiếng Việt**. Code (biến, hàm, class, comment kỹ thuật): **tiếng Anh** chuẩn Java. JSON field `snake_case`, map sang `camelCase` ở tầng model Java nhưng giữ nguyên khoá khi (de)serialize.

**Tên định danh trong code LUÔN là tiếng Anh — không có ngoại lệ cho code test.** Quy tắc này
áp dụng cho mọi tên biến, tên method, tên class, tên hàm helper, **kể cả tên method `@Test` và
helper trong file test**. Không viết tiếng Việt không dấu trong định danh.

| Sai | Đúng |
|---|---|
| `void dangKy(...)` | `void register(...)` |
| `void loginSai(...)` | `void loginWithWrongPassword(...)` |
| `sai5LanLienTiep_LanThu6DuDungPassword_VanBiAccountLocked()` | `after5FailedAttempts_6thWithCorrectPassword_isStillLocked()` |
| `void tokenBiSuaChuKy_nemSignatureException()` | `void tamperedSignature_throwsSignatureException()` |

Tên method test đặt theo mẫu `<điều kiện>_<hành động>_<kết quả mong đợi>` bằng tiếng Anh.

Chỗ ĐƯỢC dùng tiếng Việt trong file code: giá trị chuỗi dữ liệu nghiệp vụ (`"Tiền mặt"`,
`"Ăn uống"`), thông điệp lỗi trả cho người dùng, và comment giải thích — comment viết tiếng
Việt có dấu đầy đủ. Riêng định danh thì tuyệt đối tiếng Anh.
