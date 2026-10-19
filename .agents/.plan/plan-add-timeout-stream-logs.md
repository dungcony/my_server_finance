# Thêm timeout cho request Stream Logs trong Bruno

## Bối cảnh

Khi thực thi kiểm thử toàn bộ API qua Bruno CLI (`npx @usebruno/cli run . -r --env Local`), tiến trình bị đứng vô hạn tại request `02-Users/Admin/10-Stream-Logs`. Nguyên nhân do đây là endpoint Server-Sent Events (SSE), server Spring Boot giữ kết nối mở liên tục để đẩy log thời gian thực, khiến Bruno CLI đứng chờ đóng luồng stream mà không chạy tiếp các request phía sau.

## Quyết định đã chốt

- Thêm thuộc tính `timeout: 1500` (1.5 giây) vào mục `settings` của file `10-Stream-Logs.yml`. Sau 1.5 giây nhận dữ liệu ban đầu, kết nối sẽ tự động đóng để Bruno CLI tiếp tục chạy các request phía sau.

## 1. Các file sẽ thay đổi

**Tài liệu / Cấu hình Bruno**:
- `bruno/02-Users/Admin/10-Stream-Logs.yml`: Dòng 13-15, bổ sung `timeout: 1500` vào khối `settings`.

*Các file không thay đổi*: Mã nguồn Java và các request Bruno khác.

## 2. Lý do thay đổi

- Request `GET /v1/admin/logs/stream` trả về `SseEmitter` có thời gian giữ kết nối lên tới 30 phút (`AdminLogStreamService.java:95`).
- File `10-Stream-Logs.yml` trước đó chỉ có `encodeUrl: true`, không có giá trị `timeout`. Thêm `timeout: 1500` giúp giới hạn thời gian mở socket, tránh việc chạy test tự động bị treo.

## 3. Hướng giải quyết / code xem trước

Cập nhật khối `settings` trong `bruno/02-Users/Admin/10-Stream-Logs.yml`:

```yaml
settings:
  encodeUrl: true
  timeout: 1500
```

## 4. Danh sách công việc chi tiết

- Thêm dòng `timeout: 1500` vào phần `settings` của file `bruno/02-Users/Admin/10-Stream-Logs.yml`.

## Ngoài phạm vi

- Thay đổi mã nguồn Java của backend.

## Kiểm tra

- Luôn hỏi trước khi chạy, không tự ý chạy test nếu không có yêu cầu.
- Lệnh PowerShell kiểm tra Bruno CLI sau khi sửa:
  ```powershell
  cd d:\projects\java\server\bruno
  npx @usebruno/cli run 02-Users/Admin -r --env Local
  ```
  *(Kỳ vọng: Bruno CLI chạy qua request `10-Stream-Logs` và chạy tiếp `11-Unlock-User`, `khôi phục user` mà không bị kẹt vô hạn).*

## Câu hỏi còn mở

- Không còn câu hỏi mở.

## Bảng công việc

| STT | Nhiệm vụ | Trạng thái |
|---|---|---|
| 1 | Thêm `timeout: 1500` vào `bruno/02-Users/Admin/10-Stream-Logs.yml` | Hoàn thành |
