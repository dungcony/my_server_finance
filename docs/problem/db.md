# Các vấn đề về DB

## Đang thừa khi thêm 1 bảng category_group

- Về cơ bản nó chính là các category của hệ thống mà user không tạo được chỉ admin được phép tạo
- Nếu thế thì k cho user tạo các category gốc là được nhỉ chỉ cho phép user gắn vào category gốc

### Ví dụ

- Hệ thống có y tế, cộng đồng, ...
- Thì user tạo cate phải gắn vào 1 trong các cate đó

### Chỉnh sửa mới

- bỏ bảng category_group (đã xong ở migration V1)
- không cho phép user tạo cate riêng nữa mà chỉ admin được phép tạo các cate chung (đã xong: bỏ `categories.user_id`)
- chỉ cho phép các giao dịch gắn các category lá (cột `is_leaf` đã có; luật kiểm do **service** đảm nhiệm, không dùng trigger — chưa làm ở service)
- bỏ chặn các cấp độ category (đã xong: bỏ trigger giới hạn 2 cấp, thêm `level`, `path`)
- không phân biệt thu/chi: bỏ `categories.type`, danh mục không có cha là gốc

## Thừa bảng wallet_category_settings

- Không cần thiết phải khóa category theo ví làm gì (đã xoá bảng ở migration V1)

## Bảng budget

- chưa có cột đã chi (đã thêm cột `spent`, bỏ `wallet_id` và view `v_budget_progress`)