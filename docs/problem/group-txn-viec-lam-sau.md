# Việc làm sau — giao dịch nhóm chờ xác nhận

> Ghi lại 2 việc đã bàn khi viết lại `GTransactionCreateImpl` (luồng tạo giao dịch nhóm) nhưng **chưa làm**.
> Chưa có plan, chưa code. Khi làm thì lập plan riêng theo `.agents/plan-rule.md`.

## 1. Kiểm tra lại hạn mức hoàn tiền lúc người nhận xác nhận

**Vấn đề:** hoàn tiền (`REFUND`) kiểm tra hạn mức (`amount > số dư ròng`) lúc tạo, nhưng số dư chỉ bị trừ lúc người nhận xác nhận. Trong lúc chờ, số dư của người nhận có thể đổi, hoặc có thêm yêu cầu hoàn khác cho cùng người đó, nên tổng có thể vượt số dư.

**Quyết định đã chốt:** chỉ kiểm tra lại hạn mức **lúc xác nhận** (so với số dư tại thời điểm đó). Làm từ từ, chưa gấp.

**Chưa làm / để ngỏ:**
- Lớp kiểm tra thứ hai lúc tạo (hạn mức = số dư ròng trừ tổng các yêu cầu hoàn đang chờ của người nhận) — tạm không làm, xem lại nếu người dùng gặp tình huống tạo được rồi bị từ chối lúc xác nhận.
- Chỉ áp dụng cho hoàn tiền **hộ người khác** (hoàn cho chính mình thì duyệt luôn, kiểm tra hạn mức lúc tạo là đủ).
- Luồng xác nhận nằm ngoài file tạo giao dịch; cần tìm đúng chỗ khi làm.

## 2. Xóa giao dịch chưa xác nhận khi member rời nhóm

**Vấn đề:** giao dịch đang chờ (`PENDING` hoặc chờ người đứng tên xác nhận) mà người liên quan đã rời nhóm sẽ treo mãi: số dư và quỹ không được tính, không ai xác nhận được.

**Quyết định đã chốt:** khi member rời nhóm thì xóa tất cả giao dịch chưa xác nhận.

**Câu hỏi còn mở khi làm (luồng rời nhóm nằm ngoài file tạo giao dịch):**
- Xóa theo người nào: chắc chắn xóa giao dịch mà người rời là **người đứng tên**. Còn giao dịch chờ duyệt mà người rời chỉ nằm trong **danh sách chia tiền** thì xóa hay giữ? Giữ thì khi duyệt sẽ chia tiền cho người đã rời nhóm.
- Bộ đếm số giao dịch chờ trong cache (`GroupTxnCache`) phải giảm theo số giao dịch bị xóa.
- Nên xóa mềm (đã có trường ngày xóa trên giao dịch) để còn dấu vết.
