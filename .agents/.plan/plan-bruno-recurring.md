# Cập nhật bộ Bruno cho module Recurring (`RecController`)

## Bối cảnh

- `bruno/09-Recurring/User/` đã có 7 file nhưng viết theo API cũ, **lệch** với `RecController.java` hiện tại.
- Controller mới có `enable`/`disable`, quản lý `schedules`, và nhóm `admin`; chưa có Bruno cho các endpoint này.

## Quyết định đã chốt

- Chưa có (xem "Câu hỏi còn mở").

## 1. Các file sẽ thay đổi

**Tài liệu/Bruno** (thư mục `bruno/09-Recurring/`)

| File | Hành động | Nội dung |
|---|---|---|
| `User/01-List-Recurring.yml` | **xoá** | `RecController` không có `GET /recurring` (chỉ có `GET /{id}`) |
| `User/02-Get-Recurring-Detail.yml` | giữ, kiểm tra lại | `GET /recurring/{id}` |
| `User/03-Create-Recurring.yml` | sửa | body theo `RecCreateReq`: `category_id` → `category_code`, bỏ `Idempotency-Key`, `start_date` phải ≥ hôm nay |
| `User/04-Update-Recurring.yml` | sửa | `PATCH` theo `RecUpdateReq` (`frequency`+`interval` phải đi cùng nhau) |
| `User/05-Pause-Recurring.yml` | **đổi tên** → `05-Disable-Recurring.yml` | `POST /recurring/{id}/disable`, bỏ body |
| (mới) `User/06-Enable-Recurring.yml` | **mới** | `POST /recurring/{id}/enable` |
| `User/06-Run-Now.yml` | **xoá** | endpoint `run-now` không tồn tại |
| `User/07-Delete-Recurring.yml` | đánh lại số thứ tự | `DELETE /recurring/{id}` |
| (mới) `User/08-List-Schedules.yml` | **mới** | `GET /recurring/{recId}/schedules?page&size` |
| (mới) `User/09-Create-Schedule.yml` | **mới** | `POST /recurring/{recId}/schedules` (`run_date`, `amount`), lưu `scheduleId` |
| (mới) `User/10-Update-Schedule.yml` | **mới** | `PATCH /recurring/schedules/{scheduleId}` |
| (mới) `User/11-Skip-Schedule.yml` / `12-Unskip-Schedule.yml` | **mới** | `POST .../skip`, `.../unskip` |
| (mới) `User/13-Delete-Schedule.yml` | **mới** | `DELETE /recurring/schedules/{scheduleId}` |
| (mới) `Admin/01-Get-Recurring.yml`, `02-List-Schedules.yml`, `03-Delete-Recurring.yml`, `04-Delete-Schedule.yml` | **mới** | nhóm `/recurring/admin/...` (quyền `RECURRING_TXN_READ/DELETE`) |

Không đổi: code Java, `bruno/environments/*.yml` (kiểm tra xem đã có biến `recurringId`; thêm `scheduleId` nếu cần — xem câu hỏi).

## 2. Lý do thay đổi

- Bruno lệch API: `.../pause`, `.../run-now`, `GET /recurring` không có trong `RecController.java:36-137`.
- `RecCreateReq.java` dùng `categoryCode` (không phải `category_id`), không có header idempotency.

## 3. Hướng giải quyết

- Đối chiếu từng endpoint ở `RecController.java` → viết file YAML theo đúng format các module khác (header `Authorization: Bearer {{accessToken}}`, script `postResponse` lưu id vào env).
- Create: lưu `recurringId`; Create Schedule: lưu `scheduleId`.
- Admin: dùng `{{adminToken}}` nếu env đã có (xem cách `bruno/02-Users/Admin` dùng) — **chưa xác minh**.

## 4. Danh sách công việc chi tiết

1. Đọc response DTO + `RecTxnBehavierService` để chắc body/response.
2. Sửa/xoá/đổi tên các file User cũ.
3. Thêm file mới User (enable, schedules).
4. Thêm thư mục Admin.
5. Thêm biến env còn thiếu.

## Ngoài phạm vi

- `GET /recurring` (danh sách) **không tồn tại** ở backend; không thêm endpoint Java trong plan này.
- Docs `docs/api/*` có thể cũng lệch — chưa kiểm tra.

## Kiểm tra

- Không cần chạy test (chỉ file Bruno). Bạn tự chạy collection trong Bruno để thử.
- `grep -rn "pause\|run-now" bruno/09-Recurring` để chắc không còn sót.

## Câu hỏi còn mở

1. Có xoá `01-List-Recurring` và `06-Run-Now` (endpoint không tồn tại) không, hay muốn tôi thêm endpoint list vào backend (plan riêng)?
2. Có thêm cả nhóm `Admin` không?
3. Giữ tên file theo kiểu đánh số liên tục như trên, được không?

## Bảng công việc

| # | Việc | Trạng thái |
|---|---|---|
| 1 | Đối chiếu response/service | chưa làm |
| 2 | Sửa/xoá/đổi tên file User cũ | chưa làm |
| 3 | Thêm file User mới | chưa làm |
| 4 | Thêm nhóm Admin | chưa làm |
| 5 | Cập nhật env | chưa làm |
