## với giao dịch nộp quỹ

### nếu người tạo là member (chính họ)

- gửi thông báo yêu cầu trưởng nhóm hoặc thủ quỹ xác nhận.
- nếu xác nhận, gửi 1 thông báo (thêm 1 feild json data cho Noti) cho user là giao dịch đã được ghi và yêu cầu xác nhận
  ghi vào giao dịch cuủa họ
- tương tự nếu là giao dịch expense dùng tiền của personal cũng thế
- không cho phép tạo hộ người khác

### Nếu người tạo là thủ quỹ hoặc trưởng nhóm

- Gửi thông báo kèm json (data) hỏi xác nhận của user
- Nếu user đồng ý thì chuyển sang confirm
- tương tự vs các giao dịch mà dùng tiền của người khác

## Vấn đề

- cần phải thêm trạng thái (chờ người dùng duyệt)
- cần cho phép user xác nhận gtransaction tuy nhiên chỉ cho phép khi ở trạng thái chờ người dùng duyệt và có người chi
  trả (transactor) là mình