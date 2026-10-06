# Quy tắc viết plan

> Bổ sung cho mục "Lập plan" trong `converstation-rule.md`. Mục tiêu: người dùng đọc plan là biết
> chính xác sẽ đổi gì, vì sao, và kiểm tra bằng cách nào — trước khi có dòng code nào được sửa.

## Khi nào phải lập plan

- Mọi thay đổi file trong repo (code, test, migration, cấu hình, tài liệu) đều phải lập plan trước.
- Không cần plan khi:
  - Người dùng chỉ hỏi, nhờ giải thích, phân tích hoặc review (không sửa file).
  - Người dùng nói rõ bỏ qua plan cho lần đó (VD: "sửa luôn", "không cần plan").
- Việc lớn (nhiều nhóm lỗi, nhiều module) thì chia thành nhiều plan nhỏ theo thứ tự ưu tiên, mỗi plan
  duyệt riêng. Đề xuất thứ tự rồi hỏi người dùng làm plan nào trước.

## Quy trình

- Đọc code và tài liệu liên quan trước khi viết (`docs/design/<module>/`, `api/`, migration). Mọi nhận
  định trong plan phải có căn cứ `File.java:dòng`.
- Tìm chỗ mập mờ hoặc có nhiều hướng làm → hỏi người dùng trước, rồi ghi kết quả vào mục
  "Quyết định đã chốt". Không tự chốt thay người dùng.
- Trình plan qua plan mode. **Chỉ bắt đầu sửa khi người dùng bấm duyệt (proceed)**. Từ chối hoặc gửi
  kèm comment nghĩa là chưa duyệt.
- Người dùng comment vào plan:
  - Trả lời **hết** từng comment: đồng ý và sửa plan / không đồng ý kèm lý do / hỏi lại cho rõ.
  - Comment làm đổi quyết định → cập nhật mục "Quyết định đã chốt".
  - Cập nhật plan rồi trình lại. Còn comment chưa xử lý thì chưa được code.
- Đang làm mà thấy phải đi chệch plan (thêm file, đổi hướng, gặp lỗi ngoài dự kiến) → dừng lại, sửa
  plan, xin duyệt lại. Không tự quyết giữa chừng.

## Cấu trúc bắt buộc

Ba mục đánh số là bắt buộc, giữ đúng thứ tự. Các mục còn lại chỉ thêm khi có nội dung.

```markdown
# <Động từ + đối tượng thay đổi>

## Bối cảnh
## Quyết định đã chốt
## 1. Các file sẽ thay đổi
## 2. Lý do thay đổi
## 3. Hướng giải quyết / code xem trước
## Ngoài phạm vi
## Kiểm tra
## Câu hỏi còn mở
```

### Bối cảnh

- Vài câu: hiện trạng là gì, vấn đề là gì, người dùng yêu cầu gì.

### Quyết định đã chốt

- Liệt kê những điểm đã hỏi và người dùng đã chọn. Đây là căn cứ để không hỏi lại về sau.

### 1. Các file sẽ thay đổi

- Đường dẫn tính từ gốc repo, nhóm theo: **Code** · **CSDL** · **Test** · **Tài liệu**.
- Mỗi file một dòng, nói rõ đổi gì. Sửa chỗ nhỏ thì ghi kèm dòng (`GroupController.java:83`).
- Đánh dấu rõ file **mới**, file **đổi tên** (`git mv`), file **xoá**.
- Thay đổi lặp lại nhiều chỗ thì dùng bảng `Dòng | Từ | Thành`.
- Nêu rõ file **không đổi** nếu dễ bị hiểu nhầm là có liên quan.

### 2. Lý do thay đổi

- Nêu nguyên nhân gốc, không chỉ triệu chứng. Với lỗi: kịch bản tái hiện + vị trí `File.java:dòng`.
- Nếu đang vi phạm quy tắc dự án thì dẫn mục cụ thể (VD: rule 2.9 `spring-boot_struct.md`, quy tắc
  nghiệp vụ bất biến số 4 trong `CLAUDE.md` gốc, quy tắc trong `docs/design/<module>/rule.md`).

### 3. Hướng giải quyết / code xem trước

- Các bước theo thứ tự thực hiện.
- Code xem trước chỉ đưa đoạn then chốt (chữ ký hàm, câu query, nhánh logic khó), không dán cả file.
- TDD: nêu test nào viết đỏ trước và **đỏ vì khẳng định sai** ở đâu. Trường hợp không làm đỏ-xanh tự
  nhiên được (VD: bỏ giá trị enum làm lỗi biên dịch trước) thì nói rõ sẽ làm thế nào.
- Thay đổi nhạy cảm phải đánh dấu ⚠️ cho nổi bật:
  - Đổi API contract (thêm/bỏ/đổi tên trường, đổi mã lỗi) → ghi rõ ảnh hưởng tới app Flutter.
  - Đổi schema: ghi số version mới sau khi `ls src/main/resources/db/migration/`. Sửa migration đã
    chạy thì nói rõ CSDL cũ sẽ vỡ checksum.
  - Xoá code, đổi hành vi hiện tại, thêm dependency.
- Code lệch tài liệu thiết kế thì nêu luôn tài liệu nào được cập nhật trong cùng lần thay đổi.

### Ngoài phạm vi

- Vấn đề phát hiện khi đọc code nhưng **không sửa** trong plan này. Ghi lại để người dùng quyết, không
  tự sửa kèm.

### Kiểm tra

- Lệnh chạy **chỉ test liên quan** theo `test-rule.md`, VD: `mvn test -Dtest=AbcServiceImplTest,AbcIntegrationTest`.
  Kiểm tra lớp test có tồn tại trước khi đưa vào lệnh.
- Lệnh `grep` để chắc không còn sót tên cũ (khi đổi tên/bỏ khái niệm).
- Không ghi full test. Cần kết quả tổng thì ghi "bạn tự chạy full test".

### Câu hỏi còn mở

- Còn câu hỏi thì plan chưa đủ để duyệt. Ưu tiên hỏi xong trước khi trình plan.

## Quy tắc viết

- Tiếng Việt, ngắn gọn; ưu tiên bảng và gạch đầu dòng hơn đoạn văn dài.
- Tên class, hàm, trường, file giữ nguyên tiếng Anh và đặt trong backtick.
- Chỉ ghi điều đã kiểm chứng trong code. Điều chưa đọc tới thì ghi rõ "chưa xác minh".
- Không đưa vào plan những thay đổi ngoài yêu cầu (refactor diện rộng, đổi tên tiện tay, dọn code
  không liên quan).
- Code xem trước trong plan cũng tuân thủ quy tắc comment: không đánh số thứ tự trong comment.

## Sau khi làm xong

- Kill mọi task nền đã chạy.
- Chạy test liên quan đã ghi ở mục "Kiểm tra" (hoặc hỏi người dùng có muốn chạy không).
- Xem lại `git diff` đối chiếu với mục "1. Các file sẽ thay đổi": có file nào đổi mà plan không nhắc
  thì phải báo.
- Báo cáo ngắn: đã đổi gì, file nào, kết quả test, phần nào trong plan chưa làm và vì sao.
