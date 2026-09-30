# PROBLEM

## GROUP

- sửa `REFUND` nên kiểm hạn mức theo số dư **hiện tại** hay số dư **tại thời điểm giao dịch**. Đang dùng cách hiện tại, chưa quyết có đổi hay không
  - **cách hiện tại** (số dư hiện tại của người nhận, bỏ chính khoản đang sửa; chỉ kiểm khi số tiền tăng hoặc đổi người nhận)
    - lợi: đơn giản, chỉ tái dùng `BalanceCalculator` với `excludeTxnId`, một lượt duyệt
    - lợi: khớp với cách lúc tạo đang kiểm (cũng theo số dư hiện tại), hai luồng không lệch nhau
    - lợi: không cho tăng khoản hoàn / rút vượt phần người đó thực sự đang có trong quỹ hôm nay
    - hại: khoản hợp lệ lúc ghi vẫn có thể bị chặn khi sửa. Ví dụ lúc đó A có 1tr, hoàn 500k; sau này A chi tiêu còn 400k thì sửa khoản đó lên 600k bị chặn dù lúc ghi thì được
    - hại: kết quả phụ thuộc lúc bấm sửa, không phụ thuộc lúc giao dịch xảy ra
  - **cách mới** (số dư tại `occurredAt` của giao dịch, chỉ tính các khoản xảy ra trước nó)
    - lợi: trả lời đúng câu hỏi "nếu ghi khoản này đúng lúc đó thì có hợp lệ không", không bị chặn oan vì biến động về sau
    - lợi: khớp cách `BalanceCalculator` đang tính (duyệt theo `occurredAt` rồi `createdAt`)
    - lợi: ghi bù một khoản hoàn lùi ngày cũng được kiểm đúng
    - hại: phải đổi cả lúc tạo để nhất quán, nếu không hai luồng kiểm khác nhau; đụng thêm code và test
    - hại: cần thêm truy vấn hoặc bộ lọc giao dịch trước một thời điểm
    - hại: sửa một khoản cũ có thể làm `REFUND` ghi sau nó vượt hạn mức mà không ai phát hiện. Số liệu vẫn khớp (quỹ = tổng phần mọi người), chỉ là phần của một người có thể âm. Muốn chặn phải duyệt lại toàn bộ theo thứ tự thời gian
    - hại: có thể tăng một khoản hoàn cũ khi hôm nay người đó đã hết phần, làm tiền quỹ chảy ra dù phần hiện tại của họ âm
