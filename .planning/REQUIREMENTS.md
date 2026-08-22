# Requirements: Backend Quản lý tài chính cá nhân có AI

**Defined:** 2026-08-22
**Core Value:** Mọi API tuân thủ 3 nguyên tắc bất biến — ghi nhanh, AI chỉ đề xuất qua ai_drafts, riêng tư mặc định kiểm tra quyền ngay trong SQL

Nguồn: `api/00-QUY-UOC-CHUNG.md` đến `api/10-NHOM-GIA-DINH.md`, `THIET-KE-CSDL.md`. Yêu cầu dưới đây trích trực tiếp từ đặc tả đã chốt — không tự suy diễn thêm trường/API.

## v1 Requirements

### Nền tảng chung (CORE)

- [x] **CORE-01
**: Mọi response API theo khung thống nhất `{success, data}` / `{success, error}`, lỗi validate nhiều trường trả hết một lượt qua `error.fields`
- [x] **CORE-02
**: Phân trang chuẩn (`page`, `page_size` mặc định 20 tối đa 100), lọc theo `from_date/to_date` hoặc `period`, sắp xếp `sort_by`/`sort_order`
- [x] **CORE-03
**: Mọi endpoint POST tạo mới hỗ trợ header `Idempotency-Key`, nhớ kết quả trong 24 giờ
- [x] **CORE-04
**: Rate limiting theo endpoint (auth 5/phút/IP, AI 30/phút/user, còn lại 120/phút/user), trả header `X-RateLimit-*`
- [ ] **CORE-05**: Mọi truy vấn có kiểm tra quyền ngay trong câu SQL; không có quyền trả 404 (không phải 403), trừ trường hợp biết chắc tài nguyên tồn tại nhưng thiếu vai trò
- [ ] **CORE-06**: Xoá mềm (`is_deleted`) cho các bảng quan trọng; API DELETE idempotent (gọi lại vẫn 200)
- [ ] **CORE-07**: Lưu/trả timestamp (`created_at`, `updated_at`) theo UTC. **Lưu ý:** `transactions.date` là kiểu `DATE` (ngày lịch thuần, không có múi giờ) — gom nhóm báo cáo theo ngày/tháng dùng thẳng cột này, **không** chuyển đổi múi giờ. Chỉ các cột `TIMESTAMPTZ` mới cần quan tâm UTC↔giờ Việt Nam
- [ ] **CORE-08**: Số tiền là số nguyên VND, không dùng kiểu dấu phẩy động ở bất kỳ tầng nào
- [x] **CORE-09
**: Viết `db/migration/V6__va_loi_bao_mat.sql` vá ba lỗi của schema hiện có: (a) `v_budget_progress` thiếu điều kiện phạm vi người dùng — hiện cộng chi tiêu của **mọi** user trong hệ thống khi ngân sách không chỉ định `wallet_id`, ảnh hưởng cả ngân sách cá nhân lẫn nhóm; (b) `fn_category_tree` không lọc `is_deleted` ở nhánh danh mục cha; (c) `groups` thiếu cột hạn mã mời mà `api/10` yêu cầu
- [x] **CORE-10
**: Viết `db/migration/V7__ha_tang_xac_thuc.sql` tạo 4 bảng hạ tầng Phase 1 chưa có trong V1–V5: `refresh_tokens` (AUTH-03/08), `idempotency_keys` (CORE-03), `password_reset_tokens` (AUTH-06), `login_attempts` (AUTH-07). Bảng `export_jobs` cho REPORT-05 hoãn tới Phase 4 để thiết kế sát nhu cầu thật
- [x] **CORE-11
**: Trước khi lập kế hoạch mỗi phase, **bắt buộc đối chiếu requirements với schema thật** trong `db/migration/V*.sql` — không tin vào mô tả trong `api/*.md` hay tài liệu planning. `api/` và `db/` là hai tài liệu không gặp nhau ở runtime nên có thể lệch nhau âm thầm; app không dính lỗi này vì có compiler ép buộc, backend thì không

### Xác thực & tài khoản (AUTH)

- [ ] **AUTH-01**: User đăng ký bằng email/password, server tự băm mật khẩu (bcrypt ≥12 hoặc argon2id), tự tạo ví "Tiền mặt" số dư 0, sinh cặp access/refresh token
- [ ] **AUTH-02**: User đăng nhập, sai email hoặc sai mật khẩu trả cùng mã lỗi `INVALID_CREDENTIALS` để tránh dò email
- [ ] **AUTH-03**: Refresh token dùng một lần, phát hiện tái sử dụng thì thu hồi toàn bộ phiên của tài khoản
- [ ] **AUTH-04**: User đăng xuất thu hồi refresh token hiện tại; đổi mật khẩu/reset thành công thu hồi mọi refresh token khác
- [ ] **AUTH-05**: User xem/sửa hồ sơ (`full_name`, `avatar_url`) — không sửa được `email`, `plan` qua endpoint này
- [ ] **AUTH-06**: User đổi mật khẩu (yêu cầu mật khẩu cũ đúng) và quên/đặt lại mật khẩu qua email (luôn trả 200 dù email không tồn tại, mã hạn 15 phút dùng 1 lần)
- [ ] **AUTH-07**: Khoá đăng nhập tài khoản 15 phút sau 5 lần sai liên tiếp; ghi log mọi lần đăng nhập thành công/thất bại kèm IP
- [x] **AUTH-08
**: JWT access token (hạn 1 giờ) chỉ chứa id/plan/exp, không chứa dữ liệu nhạy cảm; refresh token (hạn 30 ngày) lưu bản băm trong DB

### Ví tiền (WALLET)

- [ ] **WALLET-01**: User xem danh sách ví của mình (lọc theo `type`, `only_in_total`, `include_shared`) và tổng tài sản (tách riêng cá nhân/chung, không cộng đôi)
- [ ] **WALLET-02**: User tạo ví (tên không trùng, `current_balance` = `initial_balance` lúc tạo, không sinh giao dịch nào)
- [ ] **WALLET-03**: User sửa ví — không sửa được `current_balance` và `type` qua PATCH (chỉ đổi qua giao dịch)
- [ ] **WALLET-04**: User xoá ví (mềm) — chặn nếu còn giao dịch và không xác nhận `delete_transactions`, không xoá được ví cuối cùng
- [ ] **WALLET-05**: User sắp xếp lại thứ tự hiển thị ví
- [ ] **WALLET-06**: User chuyển tiền giữa 2 ví trong 1 giao dịch DB — tạo đúng một bản ghi `type=transfer` với cả `wallet_id` và `destination_wallet_id`, mặc định cho phép chuyển dù không đủ số dư trừ khi `fail_if_insufficient=true`
- [ ] **WALLET-07**: Hệ thống đối chiếu số dư ví (thủ công qua API và tự động hằng ngày) theo công thức `initial_balance + thu - chi - chuyển đi + chuyển đến`, ghi log khi lệch

### Danh mục & biểu tượng (CAT)

- [ ] **CAT-01**: User xem danh sách danh mục dạng cây 2 cấp (gồm cả hệ thống và riêng của mình), lọc theo `type`
- [ ] **CAT-02**: User tạo danh mục cha hoặc con — chỉ 2 tầng cứng, con phải cùng `type` với cha, cha bắt buộc có `category_group_id`, con kế thừa từ cha
- [ ] **CAT-03**: User sửa danh mục — không sửa được `type`; đổi `parent_category_id` phải giữ đúng 2 tầng và không cho phép nếu danh mục đang có con
- [ ] **CAT-04**: User xoá danh mục — chặn nếu còn con, còn giao dịch (trừ khi có `replacement_category_id` cùng `type`), hoặc còn ngân sách đang dùng; danh mục hệ thống không sửa/xoá được
- [ ] **CAT-05**: User sắp xếp lại danh mục trong cùng cấp
- [ ] **CAT-06**: User xem danh sách nhóm lớn (`category_groups`, chỉ đọc) và kho biểu tượng (`icons`, lọc theo `icon_group`/`search`)

### Giao dịch (TXN)

- [ ] **TXN-01**: User xem danh sách giao dịch có phân trang, lọc đầy đủ (ngày, loại, ví, danh mục, nguồn, từ khoá, khoảng tiền, có/không transfer), kèm `summary` tính trên toàn bộ kết quả lọc và loại trừ transfer
- [ ] **TXN-02**: User xem giao dịch gom theo ngày (`by-date`) và chi tiết một giao dịch (kèm `related_debt`/`recurring`/`ai_drafts` nếu có)
- [ ] **TXN-03**: User tạo giao dịch expense/income/transfer — validate đúng ràng buộc theo `type` (category bắt buộc/rỗng, destination bắt buộc/rỗng), không cho ngày tương lai, `amount` > 0, cập nhật số dư ví trong cùng 1 DB transaction
- [ ] **TXN-04**: User tạo tối đa 50 giao dịch cùng lúc (`bulk`) — dòng lỗi bị bỏ qua và báo trong `row_errors`, không từ chối toàn bộ
- [ ] **TXN-05**: User sửa giao dịch (PUT) theo đúng 3 bước bắt buộc: hoàn tác ảnh hưởng cũ lên ví cũ → ghi giá trị mới → áp dụng ảnh hưởng mới lên ví mới, trong 1 DB transaction — không được cộng/trừ chênh lệch trực tiếp
- [ ] **TXN-06**: User xoá giao dịch (mềm) — hoàn tác ảnh hưởng số dư ví trước; nếu gắn với khoản trả nợ thì cập nhật lại `paid_amount` liên quan
- [ ] **TXN-07**: User nhân bản giao dịch sang hôm nay, có thể ghi đè ngày/số tiền
- [ ] **TXN-08**: Mọi thống kê/lọc theo danh mục cha phải cộng gộp cả giao dịch của mọi danh mục con (dùng một hàm dùng chung, không lặp điều kiện lọc ở nhiều nơi)

### Ngân sách (BUDGET)

- [ ] **BUDGET-01**: User xem danh sách ngân sách kèm tiến độ tính động lúc gọi API (không lưu cột `spent_amount`), cộng gộp danh mục con, đúng 3 trạng thái (normal/near_limit/over_limit)
- [ ] **BUDGET-02**: User xem tổng quan ngân sách kỳ hiện tại và chi tiết một ngân sách (kèm dự báo ngày hết ngân sách nếu sớm hơn `end_date`)
- [ ] **BUDGET-03**: User tạo ngân sách — `category_id` phải loại expense, chặn trùng (cùng danh mục + ví + kỳ chồng lấn), tự tính `end_date` từ `period_type`
- [ ] **BUDGET-04**: User sửa ngân sách — không sửa được `category_id` và `period_type` (phải xoá tạo lại); xoá ngân sách
- [ ] **BUDGET-05**: User xem gợi ý hạn mức từ AI (`suggested_limit` = TB chi 3 kỳ gần nhất × 1.05, làm tròn hàng trăm nghìn; trả `null` nếu <1 tháng dữ liệu)
- [ ] **BUDGET-06**: User xem danh sách cảnh báo ngân sách (severity critical/alert) kèm hành động gợi ý cụ thể
- [ ] **BUDGET-07**: Hệ thống tự động lặp kỳ ngân sách mới hằng ngày cho ngân sách `auto_renew=true` đã hết kỳ, chống tạo trùng
- [x] **BUDGET-08**: **Test riêng tư ngân sách chạy ngay tại Phase 4** (không đợi Phase 5): user A và user B cùng nhóm, B chi 1 triệu vào danh mục X, ngân sách **cá nhân** của A cho danh mục X phải hiện `spent_amount = 0`. Test này chốt vĩnh viễn lỗ hổng mà CORE-09
(a) vừa vá — nếu ai đó `CREATE OR REPLACE VIEW` làm hỏng lại, test phải đỏ ngay

### Báo cáo & thống kê (REPORT)

- [ ] **REPORT-01**: Mọi endpoint báo cáo tuân thủ 2 nguyên tắc bắt buộc: loại `type=transfer`, cộng gộp danh mục con vào cha (qua `fn_category_tree`). Gom nhóm theo ngày/tháng dùng thẳng `transactions.date` kiểu `DATE` — không chuyển đổi múi giờ
- [ ] **REPORT-02**: User xem tổng hợp màn Tổng quan (`/reports/home`) gộp đủ dữ liệu cần thiết
- [ ] **REPORT-03**: User xem báo cáo thu chi theo kỳ (`summary`), theo nhóm danh mục lớn (biểu đồ tròn), theo từng danh mục (xếp hạng, có dòng "Không phân loại")
- [ ] **REPORT-04**: User xem xu hướng chi tiêu theo ngày trong tháng (đường tích luỹ chỉ chạy tới hôm nay) và theo tháng (so sánh tối đa 24 tháng)
- [ ] **REPORT-05**: User xuất báo cáo (pdf/excel/csv) xử lý bất đồng bộ — trả 202 + `job_id`, poll trạng thái, link tải hạn 24h

### Trợ lý AI — giai đoạn rule-based (AI)

- [ ] **AI-01**: User gửi câu tiếng Việt (tối đa 2000 ký tự, tối đa 20 dòng/giao dịch) để hệ thống tách thành các bản nháp giao dịch bằng quy tắc từ khoá — không bao giờ tự tạo giao dịch thật
- [ ] **AI-02**: User gửi ảnh hoá đơn (JPEG/PNG tối đa 10MB) để hệ thống đọc bằng dữ liệu mẫu cố định (`engine=sample`) — hợp đồng response giữ nguyên để thay model thật sau này
- [ ] **AI-03**: Mọi kết quả AI lưu vào `ai_drafts` với `status=pending` kèm mức tin cậy (high/medium/low) và `missing_fields`
- [ ] **AI-04**: User duyệt một hoặc nhiều bản nháp (`approve`, `approve-bulk` tối đa 20) — phần user sửa được ưu tiên, tạo giao dịch thật, cập nhật số dư, **bắt buộc ghi `ai_drafts.user_corrections`** — đây là **cột `JSONB` trong bảng `ai_drafts`** (V2 dòng 26), không phải bảng riêng; nội dung là diff giữa AI đoán và giá trị người dùng chọn
- [ ] **AI-05**: User bỏ một bản nháp (`status=discarded`, không xoá hẳn); dọn dẹp bản `discarded` sau 90 ngày
- [ ] **AI-06**: User xem dự báo chi tiêu (kèm khoảng tin cậy, rỗng nếu <3 tháng lịch sử) và khuyến nghị/cảnh báo bất thường (mỗi khuyến nghị kèm `action_code` thực hiện được ngay)

### Sổ nợ (DEBT)

- [ ] **DEBT-01**: User tạo khoản nợ (cho vay/đi vay) — trong 1 DB transaction tạo `debt` + giao dịch thật tương ứng (Cho vay/Đi vay), cập nhật số dư ví, gắn `origin_transaction_id`
- [ ] **DEBT-02**: User xem danh sách/chi tiết/tổng quan khoản nợ (lọc type, status, quá hạn)
- [ ] **DEBT-03**: User ghi một lần trả nợ — backend chèn bản ghi `debt_payments` và tạo giao dịch thật (Thu nợ/Trả nợ). **`debts.paid_amount` và `debts.status` do trigger `trg_debt_payments_sync` (V4) sở hữu — backend TUYỆT ĐỐI không tự ghi hai cột này**, trigger tính lại bằng `SUM()` mỗi lần bảng con thay đổi. Không sửa được `principal_amount`/`type`/`wallet_id`
- [ ] **DEBT-04**: User huỷ một lần trả nợ — backend xoá bản ghi `debt_payments` và hoàn tác giao dịch liên quan; `paid_amount`/`status` tự cập nhật qua trigger (kể cả việc mở lại `outstanding` từ `settled`), backend không can thiệp
- [ ] **DEBT-05**: User đánh dấu nợ không đòi nữa (`write-off`) — chỉ đổi status, không sinh giao dịch mới
- [ ] **DEBT-06**: User xoá khoản nợ — hoàn tác toàn bộ giao dịch gốc và mọi giao dịch trả nợ liên quan, cập nhật lại số dư
- [ ] **DEBT-07**: Hệ thống nhắc nợ đến hạn (còn 7 ngày, còn 1 ngày, quá hạn nhắc lại mỗi 7 ngày)

### Giao dịch định kỳ (RECUR)

- [ ] **RECUR-01**: User tạo/sửa/xoá khoản định kỳ — `category_id` phải cùng `type`, đặt `next_run_date=start_date`, không sinh giao dịch ngay khi tạo
- [ ] **RECUR-02**: User tạm dừng/bật lại khoản định kỳ, hoặc chạy ngay (`run-now`) mà không đổi `next_run_date`
- [ ] **RECUR-03**: Hệ thống sinh giao dịch tự động hằng ngày cho khoản đến hạn — xử lý đúng ngày 29/30/31 không tồn tại (dùng ngày cuối tháng đích nhưng giữ nguyên ngày gốc cho các kỳ sau), chống ghi trùng bằng UNIQUE `(recurring_id, date)` ở DB, và bắt kịp đủ các kỳ bị bỏ lỡ nếu user vắng mặt lâu

### Mục tiêu tiết kiệm (GOAL)

- [ ] **GOAL-01**: User tạo/sửa/xoá mục tiêu tiết kiệm — `initial_amount` lúc tạo không sinh giao dịch
- [ ] **GOAL-02**: User xem đánh giá khả thi (`feasible`/`challenging`/`not_feasible`) dựa trên `monthly_required` so với thu nhập trung bình
- [ ] **GOAL-03**: User nạp tiền vào mục tiêu — hai chế độ: chuyển tiền thật (transfer, yêu cầu goal có `wallet_id`) hoặc chỉ ghi nhận tiến độ. Backend chèn bản ghi `goal_contributions`; **`savings_goals.saved_amount` và `status` do trigger `trg_goal_contributions_sync` (V4) sở hữu — backend không tự ghi**, kể cả việc chuyển `completed` khi đạt target
- [ ] **GOAL-04**: User rút lại một lần nạp — backend xoá bản ghi `goal_contributions` và hoàn tác giao dịch liên quan nếu có; `saved_amount`/`status` tự cập nhật qua trigger (kể cả mở lại `in_progress`), backend không can thiệp

### Nhóm gia đình (GROUP)

- [ ] **GROUP-01**: User tạo nhóm (tối đa 5 nhóm/người), mời thành viên qua mã (hạn 7 ngày, tối đa 10 thành viên/nhóm), tham gia bằng mã mời
- [ ] **GROUP-02**: User xem danh sách thành viên (chỉ thông tin cơ bản, không kèm số dư/ví cá nhân) và tổng quan tài chính chung
- [ ] **GROUP-03**: Owner xoá thành viên, chuyển quyền chủ nhóm; owner không rời được nhóm còn người khác nếu chưa chuyển quyền
- [ ] **GROUP-04**: User tự rời nhóm — đặt `is_active=false`, giữ nguyên lịch sử giao dịch đã ghi trên ví chung
- [ ] **GROUP-05**: Owner xoá nhóm — bắt buộc chọn xử lý ví chung (`transfer_to_owner` hoặc `delete_all`)
- [ ] **GROUP-06**: Dữ liệu cá nhân (ví/giao dịch/ngân sách/nợ/mục tiêu riêng) tuyệt đối không hiển thị cho bất kỳ thành viên nào khác kể cả owner — **bắt buộc có bộ test tự động xác nhận user A không đọc được dữ liệu cá nhân của user B**

### Vận hành nền (JOB)

- [ ] **JOB-01**: Job đối chiếu số dư ví chạy hằng ngày cho toàn bộ ví, tự sửa và ghi log khi lệch
- [ ] **JOB-02**: Job tự động lặp kỳ ngân sách hằng ngày, chống tạo trùng
- [ ] **JOB-03**: Job sinh giao dịch định kỳ hằng ngày, chống trùng bằng ràng buộc DB, bắt kịp kỳ bỏ lỡ
- [ ] **JOB-04**: Job nhắc nợ đến hạn theo lịch quy định
- [ ] **JOB-05**: Job dọn dẹp `ai_drafts` trạng thái `discarded` sau 90 ngày

## v2 Requirements

Chưa nằm trong roadmap hiện tại — ghi nhận để không quên.

### Mở rộng tương lai

- **FUTURE-01**: Đa tiền tệ (cột `currency`, bảng tỉ giá)
- **FUTURE-02**: Mô hình AI thật thay cho rule-based/sample (dùng dữ liệu `user_corrections` đã tích luỹ)
- **FUTURE-03**: Chia tiền trong nhóm (bill splitting)
- **FUTURE-04**: Nhật ký thay đổi chi tiết (audit trail bảng `history`)
- **FUTURE-05**: Đính kèm nhiều ảnh hoá đơn (bảng `attachments` thay cho `receipt_url`)
- **FUTURE-06**: Nhãn tự do (label) cho giao dịch

## Out of Scope

| Feature | Reason |
|---------|--------|
| Huấn luyện mô hình AI riêng (NLU/OCR) | Giai đoạn này dùng rule-based + sample data; hợp đồng API giữ nguyên để thay sau khi có đủ `user_corrections` |
| Đa tiền tệ | Chỉ dùng VND ở v1, đã ghi rõ trong THIET-KE-CSDL.md mục 7 |
| UI mobile cho Sổ nợ/Định kỳ/Mục tiêu/Nhóm gia đình | API backend vẫn xây đủ theo đặc tả; UI các màn này "chưa có trong thiết kế 2a" theo ghi chú tại api/08, 09, 10 |
| Multi-instance/horizontal scaling cho background job | Đồ án tốt nghiệp chạy 1 instance, dùng `@Scheduled` là đủ, không cần Quartz cluster |
| Chia tiền trong nhóm (bill splitting) | Chưa có trong thiết kế hiện tại |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CORE-01 | Phase 1 | Done |
| CORE-02 | Phase 1 | Done |
| CORE-03 | Phase 1 | Pending |
| CORE-04 | Phase 1 | Pending |
| CORE-05 | Phase 1 | Pending |
| CORE-06 | Phase 1 | Pending |
| CORE-07 | Phase 1 | Pending |
| CORE-08 | Phase 1 | Pending |
| CORE-09 | Phase 1 | Done |
| CORE-10 | Phase 1 | Done |
| CORE-11 | Phase 1 | Done |
| AUTH-01 | Phase 1 | Pending |
| AUTH-02 | Phase 1 | Pending |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Pending |
| AUTH-05 | Phase 1 | Pending |
| AUTH-06 | Phase 1 | Pending |
| AUTH-07 | Phase 1 | Pending |
| AUTH-08 | Phase 1 | Done |
| WALLET-01 | Phase 2 | Pending |
| WALLET-02 | Phase 2 | Pending |
| WALLET-03 | Phase 2 | Pending |
| WALLET-04 | Phase 2 | Pending |
| WALLET-05 | Phase 2 | Pending |
| WALLET-06 | Phase 2 | Pending |
| WALLET-07 | Phase 2 | Pending |
| CAT-01 | Phase 2 | Pending |
| CAT-02 | Phase 2 | Pending |
| CAT-03 | Phase 2 | Pending |
| CAT-04 | Phase 2 | Pending |
| CAT-05 | Phase 2 | Pending |
| CAT-06 | Phase 2 | Pending |
| TXN-01 | Phase 3 | Pending |
| TXN-02 | Phase 3 | Pending |
| TXN-03 | Phase 3 | Pending |
| TXN-04 | Phase 3 | Pending |
| TXN-05 | Phase 3 | Pending |
| TXN-06 | Phase 3 | Pending |
| TXN-07 | Phase 3 | Pending |
| TXN-08 | Phase 3 | Pending |
| BUDGET-01 | Phase 4 | Pending |
| BUDGET-02 | Phase 4 | Pending |
| BUDGET-03 | Phase 4 | Pending |
| BUDGET-04 | Phase 4 | Pending |
| BUDGET-05 | Phase 4 | Pending |
| BUDGET-06 | Phase 4 | Pending |
| BUDGET-07 | Phase 4 | Pending |
| BUDGET-08 | Phase 4 | Pending |
| REPORT-01 | Phase 4 | Pending |
| REPORT-02 | Phase 4 | Pending |
| REPORT-03 | Phase 4 | Pending |
| REPORT-04 | Phase 4 | Pending |
| REPORT-05 | Phase 4 | Pending |
| DEBT-01 | Phase 4 | Pending |
| DEBT-02 | Phase 4 | Pending |
| DEBT-03 | Phase 4 | Pending |
| DEBT-04 | Phase 4 | Pending |
| DEBT-05 | Phase 4 | Pending |
| DEBT-06 | Phase 4 | Pending |
| DEBT-07 | Phase 4 | Pending |
| RECUR-01 | Phase 4 | Pending |
| RECUR-02 | Phase 4 | Pending |
| RECUR-03 | Phase 4 | Pending |
| GOAL-01 | Phase 4 | Pending |
| GOAL-02 | Phase 4 | Pending |
| GOAL-03 | Phase 4 | Pending |
| GOAL-04 | Phase 4 | Pending |
| JOB-01 | Phase 4 | Pending |
| JOB-02 | Phase 4 | Pending |
| JOB-03 | Phase 4 | Pending |
| JOB-04 | Phase 4 | Pending |
| GROUP-01 | Phase 5 | Pending |
| GROUP-02 | Phase 5 | Pending |
| GROUP-03 | Phase 5 | Pending |
| GROUP-04 | Phase 5 | Pending |
| GROUP-05 | Phase 5 | Pending |
| GROUP-06 | Phase 5 | Pending |
| AI-01 | Phase 5 | Pending |
| AI-02 | Phase 5 | Pending |
| AI-03 | Phase 5 | Pending |
| AI-04 | Phase 5 | Pending |
| AI-05 | Phase 5 | Pending |
| AI-06 | Phase 5 | Pending |
| JOB-05 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 84 total
- Mapped to phases: 84/84 ✓
- Unmapped: 0

*Cập nhật 2026-08-22: thêm CORE-09 (migration V6 vá lỗi bảo mật), CORE-10 (migration V7 hạ tầng xác thực), CORE-11 (bắt buộc đối chiếu schema thật trước mỗi phase), BUDGET-08 (test riêng tư ngân sách chạy sớm ở Phase 4) sau khi review đối chiếu planning với `db/migration/V1–V5`.*

---
*Requirements defined: 2026-08-22*
*Last updated: 2026-08-22 after roadmap creation (5 phase, coverage 100%)*
