# Các vấn đề về DB

## Đang thừa khi thêm 1 bảng category_group

- Về cơ bản nó chính là các category của hệ thống mà user không tạo được chỉ admin được phép tạo
- Nếu thế thì k cho user tạo các category gốc là được nhỉ chỉ cho phép user gắn vào category gốc

### Ví dụ

- Hệ thống có y tế, cộng đồng, ...
- Thì user tạo cate phải gắn vào 1 trong các cate đó

### Chỉnh sửa mới

- bỏ bảng category_group
- không cho phép user tạo cate riêng nữa mà chỉ admin được phép tạo các cate chung
- chỉ cho phép các giao dịch gắn các category lá
- bỏ chặn các cấp độ category

## Thừa bảng wallet_category_settings

- Không cần thiết phải khóa category theo ví làm gì