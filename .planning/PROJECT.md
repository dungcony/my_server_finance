# Backend Quản lý tài chính cá nhân có AI

## What This Is

Backend REST API (Spring Boot, Java 17) phục vụ ứng dụng di động Flutter quản lý tài chính cá nhân có AI. Cung cấp toàn bộ nghiệp vụ: xác thực, ví, danh mục, giao dịch, ngân sách, báo cáo, trợ lý AI (rule-based tạm thời), sổ nợ, giao dịch định kỳ, mục tiêu tiết kiệm, nhóm gia đình. Toàn bộ hợp đồng API đã được đặc tả sẵn tại `api/*.md` và schema tại `THIET-KE-CSDL.md` — nhiệm vụ của backend là **hiện thực hoá đúng theo đặc tả**, không tự sáng tác thêm.

## Core Value

Mọi API phải tuân thủ đúng 3 nguyên tắc bất biến của dự án: ghi giao dịch nhanh (không thêm bước chặn), AI chỉ đề xuất qua `ai_drafts` chờ duyệt (không tự ghi `transactions`), và riêng tư mặc định (mọi truy vấn ví/giao dịch/ngân sách phải kiểm tra quyền ngay trong câu SQL, trả 404 khi không có quyền).

## Requirements

### Validated

(Chưa có — đây là lần khởi tạo đầu tiên của backend)

### Active

- [ ] Xác thực & tài khoản (đăng ký, đăng nhập, JWT access/refresh, đổi/quên mật khẩu)
- [ ] Ví tiền (CRUD, chuyển tiền, đối chiếu số dư, tổng tài sản cá nhân/chung)
- [ ] Danh mục & biểu tượng (cây 2 cấp, nhóm lớn, kho icon hệ thống)
- [ ] Giao dịch (CRUD, bulk, cộng gộp danh mục con, atomicity cập nhật số dư)
- [ ] Ngân sách (tính động, cảnh báo, gợi ý AI, tự động lặp kỳ)
- [ ] Báo cáo & thống kê (tổng quan, theo danh mục, xu hướng, xuất file bất đồng bộ)
- [ ] Trợ lý AI giai đoạn 1 — rule-based parse-text, sample-data OCR, ai_drafts + user_corrections
- [ ] Sổ nợ (cho vay/đi vay sinh giao dịch thật, trả nhiều lần)
- [ ] Giao dịch định kỳ (sinh tự động, chống trùng, bắt kịp kỳ bỏ lỡ)
- [ ] Mục tiêu tiết kiệm (nạp/rút, đánh giá khả thi)
- [ ] Nhóm gia đình (ví/ngân sách chung, riêng tư cá nhân tuyệt đối, kiểm thử tự động A không đọc được B)
- [ ] Idempotency-Key cho mọi endpoint tạo mới
- [ ] Background jobs: đối chiếu số dư ví, tự động lặp ngân sách, sinh giao dịch định kỳ, nhắc nợ đến hạn, dọn ai_drafts discarded 90 ngày

### Out of Scope

- Mô hình AI thật (NLU/OCR huấn luyện riêng) — dùng rule-based + sample data ở giai đoạn này, hợp đồng API giữ nguyên để thay sau
- Đa tiền tệ — chỉ VND, để đó cho milestone sau (đã ghi ở THIET-KE-CSDL.md mục 7)
- Chia tiền trong nhóm (bill splitting) — chưa có trong thiết kế hiện tại
- Nhật ký thay đổi (audit trail) chi tiết — chưa cần cho v1
- UI mobile cho Sổ nợ, Định kỳ/Mục tiêu, Nhóm gia đình — API backend vẫn xây đủ, UI sẽ bổ sung sau ở phía app

## Context

- App Flutter (Riverpod, feature-first) đã có khung, provider, mock data qua Dio interceptor — sẵn sàng chuyển sang gọi API thật bằng `--dart-define=USE_MOCK=false`.
- Toàn bộ đặc tả API đã hoàn thiện và nhất quán tại `api/00-QUY-UOC-CHUNG.md` đến `api/10-NHOM-GIA-DINH.md`.
- Schema CSDL logic đã hoàn thiện tại `THIET-KE-CSDL.md`, migration Flyway V1-V5 thật đã chạy được trên PostgreSQL tại `db/migration/` (thư mục gốc DATN).
- JDK 17 đã cài sẵn trên máy phát triển, dùng chung cho Gradle (Android) và Spring Boot.
- Response format thống nhất `{success, data}` / `{success, error}`, lỗi phân quyền trả 404 (không phải 403) để không lộ tồn tại bản ghi.
- Ba lỗi nghiệp vụ được tài liệu nhấn mạnh là "dễ mắc nhất, khó phát hiện nhất": (1) quên cộng gộp danh mục con vào cha ở mọi thống kê, (2) quên loại `type=transfer` khỏi báo cáo, (3) sửa/xoá giao dịch không theo đúng 3 bước hoàn tác-ghi mới-áp dụng mới.

## Constraints

- **Tech stack**: Java 17, Spring Boot (bản mới nhất dòng 3.3.x/3.4.x), Maven, PostgreSQL 14+, Flyway — đã chốt, không đổi sang framework khác trừ khi người dùng yêu cầu.
- **Migration**: dùng lại `db/migration/` ở thư mục gốc DATN (không copy) — một nguồn sự thật duy nhất cho schema. **Migration mới của backend viết thẳng vào thư mục đó** (V6, V7, …), không tạo thư mục migration riêng.
- **Không viết trigger CSDL cho số dư ví** (`wallets.current_balance`) — logic này nằm ở tầng service, gói trong transaction. Lý do ghi ở `db/README.md`. **Ngoại lệ đã tồn tại trong schema:** `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) sở hữu `paid_amount`/`saved_amount`/`status` của debt/goal — backend không ghi các cột đó.
- **Không tự bịa API hoặc trường dữ liệu** chưa có trong `api/*.md` — nếu thiếu/mâu thuẫn, dừng lại hỏi thay vì đoán.
- **Ngôn ngữ**: giao tiếp/commit message tiếng Việt; code (biến, hàm, class, comment) tiếng Anh chuẩn Java; JSON field `snake_case`, map sang `camelCase` ở tầng model Java nhưng giữ nguyên khoá khi (de)serialize.
- **Số tiền**: luôn là số nguyên VND, không dùng kiểu dấu phẩy động ở bất kỳ tầng nào.
- **Múi giờ**: lưu UTC, nhưng gom nhóm báo cáo theo ngày/tháng phải tính theo giờ Việt Nam (UTC+7).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Đặt project tại `source/server` (không phải `source/backend` như CLAUDE.md ghi) | Người dùng chủ động chọn tên khác | ✓ Good — đã cập nhật CLAUDE.md gốc và MOI-TRUONG-PHAT-TRIEN.md (commit f896af6) |
| Maven làm build tool | Phổ biến cho đồ án tốt nghiệp, cấu hình rõ ràng | — Pending |
| Spring Boot 3.3.x/3.4.x mới nhất | Khớp JDK 17 đã cài sẵn, bản ổn định hiện tại | — Pending |
| Dùng lại `db/migration/` gốc, không copy vào `source/server` | Một nguồn sự thật duy nhất, khớp CLAUDE.md ("api/ và db/ là nguồn sự thật") | — Pending |
| Requirements trích trực tiếp từ `api/*.md` có sẵn, bỏ qua research nghiệp vụ | Nghiệp vụ đã chốt kỹ, research lại là lãng phí | — Pending |
| Research nhanh riêng cho Spring Boot stack/best-practice 2025 | Tài liệu môi trường chưa có version cụ thể ngoài Java 17 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-22 after initialization*
