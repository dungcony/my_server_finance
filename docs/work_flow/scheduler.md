# SCHEDULER

## reconcil wallet

- lịch sẽ chạy vào lúc 1:00 AM mỗi ngày
- sẽ cộng tổng số dư của giao dịch rồi đưa vào con số
- sau đó sẽ so sánh với wallet.balance
- nếu khác nhau sẽ cập nhật
- mỗi ví bị sửa ghi một dòng vào `reconcil_history` (số dư cũ, số dư đúng), trong cùng câu SQL đối chiếu
- bình thường số dư đã được trigger `trg_transactions_wallet_balance` cập nhật theo từng giao dịch, job này chỉ là lưới an toàn