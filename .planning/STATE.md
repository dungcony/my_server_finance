# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-22)

**Core value:** Mọi API tuân thủ 3 nguyên tắc bất biến — ghi giao dịch nhanh, AI chỉ đề xuất qua `ai_drafts` chờ duyệt, riêng tư mặc định kiểm tra quyền ngay trong câu SQL
**Current focus:** Phase 1 — Nền tảng & Xác thực

## Current Position

Phase: 1 of 5 (Nền tảng & Xác thực)
Plan: chưa lập (chờ `/gsd-plan-phase 1`)
Status: Ready to plan
Last activity: 2026-08-22 — Review đối chiếu planning với `db/migration/V1–V5`, thêm CORE-09/10/11 + BUDGET-08 (84 requirements), sửa mô tả sai về múi giờ và ranh giới trigger

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: - min
- Total execution time: 0 giờ

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Granularity coarse → 5 phase, gộp Auth vào Nền tảng, Wallet+Category chung 1 phase, Budget/Debt/Recurring/Goal/Report/JOB chung 1 phase
- [Roadmap]: Group đặt gần cuối (Phase 5, trước AI) vì là tầng quyền phủ lên wallet/transaction/budget đã ổn định — xem "Ghi chú về thứ tự phase" trong ROADMAP.md
- [Roadmap]: Testcontainers PostgreSQL bắt buộc từ Phase 3 (giao dịch) và dùng lại cho bộ test riêng tư nhóm ở Phase 5

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- Hai điểm rủi ro nghiệp vụ cao nhất cần research sâu hơn khi lập plan chi tiết: transaction 3-bước sửa/xoá (Phase 3) và quyền riêng tư nhóm gia đình (Phase 5)
- **Phase 1 phải viết migration V6/V7 trước khi viết code Java** (CORE-09/CORE-10) — V1–V5 chỉ có 15 bảng, thiếu `refresh_tokens`/`idempotency_keys`/`password_reset_tokens`/`login_attempts`. Không có V7 thì AUTH-03 chết ngay task đầu
- **`v_budget_progress` có lỗ hổng quyền chưa vá** (thiếu điều kiện phạm vi người dùng, cộng chi tiêu của mọi user khi ngân sách không chỉ định ví) — CORE-09 vá, BUDGET-08 test chốt ở Phase 4
- **Ranh giới trigger dễ hiểu sai:** "không viết trigger" chỉ áp dụng cho số dư ví. `paid_amount`/`saved_amount`/`status` của debt/goal do trigger V4 sở hữu, backend không ghi — xem chi tiết ở Phase 4 trong ROADMAP.md

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 | FUTURE-01 đến FUTURE-06 (đa tiền tệ, AI thật, bill splitting, audit trail, multi-attachment, label) | Deferred | Định nghĩa requirements ban đầu |

## Session Continuity

Last session: 2026-08-22
Stopped at: Roadmap 5 phase đã tạo xong, coverage 80/80 requirements đã xác nhận, chờ user duyệt trước khi chạy `/gsd-plan-phase 1`
Resume file: None
