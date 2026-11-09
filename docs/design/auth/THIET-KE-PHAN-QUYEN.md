# TÀI LIỆU THIẾT KẾ PHÂN QUYỀN (LƯU TRỮ LỊCH SỬ / DEPRECATED)

> ⚠️ **LƯU Ý:** Tài liệu này phản ánh kiến trúc phân quyền cũ thời điểm dự án còn kết hợp "ví chung nhóm gia đình" và phân hệ xác thực vào chung một file.  
> Hiện tại, toàn bộ thiết kế hệ thống đã được tái cấu trúc và phân tách thành các bộ tài liệu độc lập, chuẩn hóa:

1. **Phân hệ Xác thực (Authentication & Security):**
   - Thực thể & mô hình lưu trữ: [lop-thuc-the.md](lop-thuc-the.md)
   - Đặc tả API: [api.md](api.md)
   - Luồng xử lý & Bảo mật: [pipeline.md](pipeline.md)
   - Quy tắc & Bất biến: [rule.md](rule.md)

2. **Phân hệ Quản lý Người dùng & Phân quyền Hệ thống (User & Admin RBAC):**
   - Thực thể & RBAC Schema: [../user/lop-thuc-the.md](../user/lop-thuc-the.md)
   - Đặc tả API Admin & User Profile: [../user/api.md](../user/api.md)
   - Luồng nghiệp vụ: [../user/pipeline.md](../user/pipeline.md)
   - Quy tắc phân quyền: [../user/rule.md](../user/rule.md)

3. **Phân hệ Nhóm chung quỹ (Group Fund Management):**
   - Toàn bộ thiết kế mới: [../group/](../group/) (bao gồm `lop-thuc-the.md`, `api.md`, `pipeline.md`, `rule.md`, `use-case.md`).
