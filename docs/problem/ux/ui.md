# UX/UI Issues — Đã giải quyết

## [ĐÃ SỬA] Member gọi sai API giao dịch
- **Vấn đề**: Member click vào giao diện giao dịch lại gọi `/transactions/all` thay vì `/transactions` (general)
- **Giải pháp**: Sửa `group-transactions.js` và `group-detail.js` — Member gọi `/transactions` (generalList), Reviewer/Owner gọi `/transactions/all`

## [ĐÃ SỬA] Nút quản trị hiển thị với user thường
- **Vấn đề**: Nút "🛡️ Quản trị" hiện với mọi user dù bấm vào bị đá ra; trang quản trị phải riêng biệt
- **Giải pháp**: Sửa `layout.js` — nút "Quản trị" chỉ render khi `isAdmin`; khu vực admin có sidebar/topbar riêng

## [ĐÃ SỬA] Bảng balances hiển thị 0đ
- **Vấn đề**: `loadBalances` dùng sai field names không khớp với DTO backend (`GroupBalanceReportRes`, `GroupBalanceItemRes`)
- **Giải pháp**: Sửa `group-report.js` và `group-members.js` — mapping đúng fields: `fund_balance`, `total_refunded`, `total_share_amount`, `net_balance`, `needed_contribution`, `is_settlement_enabled`; sửa header bảng trong `group-detail.html` từ 7 cột → 6 cột khớp DTO

## [ĐÃ SỬA] Payload unlock user gửi sai field
- **Vấn đề**: `users.js` gửi `{ userId }` nhưng backend `UnlockUserRequest` yêu cầu `user_id` (SNAKE_CASE)
- **Giải pháp**: Sửa `admin/js/users.js` — body gửi cả `user_id` và `userId`