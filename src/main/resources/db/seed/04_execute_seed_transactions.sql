-- =============================================================
-- Thực thi nạp 1.000.000 giao dịch và hơn 3.500.000 dòng chia tiền
-- Chú ý quan trọng khi dùng Database Client trong VS Code:
-- Hãy chạy file này độc lập (Single-statement query) để procedure
-- có quyền gọi COMMIT giải phóng bộ nhớ từng đợt mà không bị lỗi
-- "invalid transaction termination".
-- =============================================================

CALL seed_bulk_group_transactions(1000000, 100000);
