Ghi tiền túi trong nhóm sang sổ cá nhân (ví mặc định) bằng event
Một plan duy nhất. Các phần phụ thuộc nhau nên làm theo đúng thứ tự ở mục 3.

Bối cảnh
Hiện CONTRIBUTION, EXPENSE bằng tiền túi (PERSONAL) và REFUND chỉ ghi ở sổ nhóm. Ví cá nhân không bị đụng tới
(docs/design/group/rule.md quyết định 4, api.md:589).
Yêu cầu: mọi giao dịch nhóm liên quan tiền túi của một cá nhân đều phải ghi sang sổ cá nhân (ví mặc định) bằng event:
nhóm bắn event, module transaction nghe và ghi. Sửa/xoá/reject giao dịch nhóm cũng bắn event để sổ cá nhân tự cập nhật.
Việc này đảo ngược quyết định 4, nên cập nhật tài liệu thiết kế cùng lần làm.
Đã có sẵn: TransactionWriter.write () nhận id do người gọi truyền (TransactionWriteCommand.java:26), có countsInReport;
TransactionService.update/delete (userId, transactionId, ...) (TransactionService.java:43,52);
NotificationService.createNotification (...) (NotificationService.java:70).
ContributeTransactionCreateEvent là record rỗng, chưa ai bắn hay nghe
(group/events/ContributeTransactionCreateEvent.java:3), tên chỉ nói đến nộp quỹ. Event phải dùng chung nhiều loại giao
dịch nên thay bằng event chung.