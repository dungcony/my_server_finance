# Finance AI Server - Workspace Rules

## Lập plan

- luôn lập file plan để bàn bạc khi muốn thay đổi gì đó
- chỉ xác nhận khi tôi bấm process trong file plan chứ k được tự ý code
- luôn xác nhận toàn bộ thắc mặc cmt trogn file plan
- cấu túc luôn là 1. các file sẽ thay đổi 2. lý do thay đổi 3. hướng giải quyết/code xem trước (nếu có)

## Task

- chạy task thì khi xong phải kill nền k cho chạy nền nữa

## Khi bắt đầu thay đổi code

- Khi tôi chưa bảo thay đổi code thì chưa được code

## General

- Trả lời ngắn gọn, tập trung vào vấn đề.
- Luôn trả lời bằng tiếng Việt.
- Hỏi gì thì phải trả lời đó hỏi logic code thì trả lời logic code không nói gì về compiler
- Hỏi code ở class nào thì chỉ tập trung vào class đó
- Trước khi sửa code, đọc và hiểu code hiện tại cùng các phần liên quan.
- Không thay đổi những phần không liên quan đến yêu cầu.
- Ưu tiên giải pháp đơn giản, rõ ràng và dễ bảo trì.
- Không thêm dependency mới nếu không thực sự cần thiết.
- Giữ nguyên coding style và convention hiện tại của project.
- Khi chạy kiểm thử hoặc build nền (Background Tasks), sau khi có kết quả phải chủ động gọi `manage_task(kill)` để đóng task, không để task treo trên giao diện.
- luôn đưa ra những file sửa trước khi bắt đầu
- luôn đưa ra 1 file lan để tôi đọc và đưa ra comment và cần tôi xác nhận đồng ý trước khi bắt đầu thực hiện thay đổi

## Code Rules

- Tuân theo các nguyên tắc SOLID khi phù hợp.
- Không áp dụng SOLID một cách máy móc nếu làm code phức tạp không cần thiết.
- Ưu tiên readability, maintainability và tính nhất quán với codebase hiện tại.
- Không tạo abstraction, interface hoặc layer mới nếu chưa có nhu cầu thực tế.
- Tránh over-engineering.

## Code Changes

- Khi sửa lỗi, xác định nguyên nhân gốc trước khi sửa.
- Không che lỗi bằng workaround nếu có thể sửa nguyên nhân.
- Trước khi thay đổi, kiểm tra các file và thành phần liên quan.
- Sau khi sửa, kiểm tra các ảnh hưởng liên quan.
- Không tự ý refactor diện rộng khi người dùng chỉ yêu cầu một thay đổi nhỏ.
- Không tự ý sửa code nếu người dùng chỉ yêu cầu giải thích, phân tích hoặc review.
- Nếu người dùng yêu cầu sửa code rõ ràng thì có thể thực hiện mà không cần hỏi lại.
- Chỉ hỏi lại khi yêu cầu chưa rõ, có nhiều hướng xử lý quan trọng, hoặc thay đổi có thể vượt ra ngoài phạm vi người dùng yêu cầu.
- Không tự ý xóa code, đổi API, thay đổi database schema hoặc thay đổi hành vi hiện tại nếu chưa được yêu cầu.
- Ưu tiên thay đổi nhỏ nhất có thể để giải quyết đúng vấn đề.
- Khi viết cmt hãy viết theo // + giải thích không cần phải ghi 1. 2.

## QUY TẮC COMMENT CODE (BẮT BUỘC):

- TUYỆT ĐỐI KHÔNG đánh số thứ tự trong comment (CẤM: `// 1.`, `// 2.`, `// 1/`, `// Bước 1:`...).
- ĐÚNG: `// xác thực người thực hiện`
- SAI: `// 1. xác thực người thực hiện`
- Trước khi trả code, bắt buộc rà soát lại: nếu thấy có số thứ tự trong comment thì phải xóa ngay.

## Python

- Tuân theo PEP 8 và convention hiện tại của project.
- Dùng type hints khi project đã áp dụng; không ép thêm nếu codebase chưa dùng.
- Không tạo class/abstraction không cần thiết cho script hay hàm đơn giản.
- Tận dụng thư viện chuẩn hoặc dependency đã có trong project trước khi thêm mới.
- Giữ đúng cấu trúc module/package hiện tại của project.
- Nếu project dùng framework cụ thể (FastAPI, Flask, Django...), giữ đúng kiến trúc, convention và cách tổ chức route/service/model hiện tại của framework đó.

## Spring Boot

- Giữ đúng kiến trúc hiện tại của project.
- Không tự ý thay đổi cấu trúc Controller, Service, Repository nếu không cần thiết.
- Không thêm dependency Spring mới nếu project đã có cách giải quyết tương đương.
- Giữ nhất quán cách xử lý exception, validation, DTO và response hiện tại.
- Không đặt business logic vào Controller.
- Không truy cập Repository trực tiếp từ Controller nếu project đang sử dụng Service layer.
- Giữ nguyên API contract trừ khi người dùng yêu cầu thay đổi.
- Khi viết javadocs thì ở class sẽ @link vào các hàm và giải thích sơ qua hàm (hàm để làm gì)

## Before Editing

Trước khi sửa code:

1. Xác định yêu cầu của người dùng.
2. Đọc file cần sửa.
3. Đọc các class/function liên quan khi cần thiết.
4. Xác định nguyên nhân hoặc mục tiêu thay đổi.
5. Chọn thay đổi nhỏ nhất giải quyết được vấn đề.
6. Chỉ chỉnh sửa những file cần thiết.

Nếu người dùng chỉ yêu cầu phân tích hoặc hỏi nguyên nhân, không sửa file.

## After Editing

Sau khi sửa:

- Đặt câu hỏi xem tôi có muốn chạy test để kiểm tra không
- Kiểm tra syntax và compile/lint error nếu có thể.
- Chạy test liên quan nếu project có test.
- Kiểm tra diff để đảm bảo không có thay đổi ngoài ý muốn.
  Không tự sửa thêm những vấn đề không liên quan vừa phát hiện.

Nếu phát hiện vấn đề khác, thông báo cho người dùng thay vì tự ý sửa.

## Response

Khi chỉ phân tích vấn đề:

- Nêu nguyên nhân.
- Nêu vị trí liên quan.
- Đề xuất cách xử lý.
- Không sửa code nếu chưa được yêu cầu.

Khi đã sửa code, mô tả ngắn:

- Lỗi/nguyên nhân.
- Đã thay đổi gì.
- File nào đã thay đổi.
- Cách kiểm tra.
