# Plan: Chỉnh lại `ReportServiceImpl` — chỉ gọi service, phép tính gom về `BalanceCalculator`

- **Phạm vi:** `ReportService` / `ReportServiceImpl` (`getSummary`, `getBalances`)
- **Trạng thái:** ⏳ CHỜ DUYỆT — chưa sửa dòng code nào
- **Xác nhận:** `[ ] PROCESS`
- **Ghi chú:** lần trước tôi đã lỡ sửa trước khi có plan. Đã hoàn tác toàn bộ, các file trở về đúng trạng thái bạn để
  lại.
- **Cập nhật theo góp ý:** mọi hàm tính toán tập trung tại `BalanceCalculator`; service chỉ lấy dữ liệu thô.

---

## 0. Hiện trạng: file đang không biên dịch được

Sau đợt refactor của bạn, `ReportServiceImpl` đã bỏ field `groupTransactionRepository` (thay bằng
`GTransactionService gTransactionService`) nhưng phần thân vẫn còn code cũ:

| #  | Lỗi                                                                                                                             | Vị trí          |
|----|---------------------------------------------------------------------------------------------------------------------------------|-----------------|
| L1 | `groupTransactionRepository` không còn là field nhưng vẫn gọi (2 lần trong `getSummary`, 1 lần trong `getBalances`)             | dòng 75, 77, 99 |
| L2 | Dùng `GTransaction` nhưng không import (entity)                                                                                 | dòng 99         |
| L3 | `BalanceCalculator.calculateBalances(..., List<Member> ...)` nhận **entity** `Member`, nhưng truyền vào `List<MemberRes>` (DTO) | dòng 106        |
| L4 | `gTransactionService` được inject mà **chưa dùng ở đâu**                                                                        | dòng 49         |

Ngoài lỗi biên dịch còn **một lỗi logic** ẩn sau đợt refactor (xem mục 2, L5).

---

## 1. Các file sẽ thay đổi

Nguyên tắc (theo yêu cầu của bạn): **mọi phép tính nằm ở `BalanceCalculator`**. Service chỉ làm một việc là lấy dữ liệu
thô rồi đưa cho `BalanceCalculator`.

| File                                                     | Thay đổi                                                                                                                                  |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `group/helper/BalanceCalculator.java`                    | Thêm `calculateBalancesByPeriods(...)` và `sumAmountByTypeInPeriod(...)`; hàm cũ `calculateBalances` giữ nguyên chữ ký, gọi sang hàm mới |
| `group/service/GTransactionService.java`                 | Thêm **1** hàm lấy dữ liệu thô: `findConfirmedTransactions(groupId)` (không có hàm tính toán nào)                                          |
| `group/service/impl/GTransactionServiceImpl.java`        | Hiện thực hàm trên (chỉ gọi `transactionRepository`)                                                                                      |
| `group/service/MemberService.java`                       | Thêm **1** hàm lấy dữ liệu thô: `findMemberPeriods(groupId)`                                                                              |
| `group/service/impl/MemberServiceImpl.java`              | Hiện thực hàm trên                                                                                                                        |
| `group/service/impl/ReportServiceImpl.java`              | Bỏ hết repository; lấy dữ liệu qua 2 service rồi gọi `BalanceCalculator`                                                                  |
| `src/test/.../group/helper/BalanceCalculatorTest.java`   | Thêm test cho 2 hàm mới                                                                                                                   |
| `src/test/.../group/service/GroupReportServiceTest.java` | Đổi mock `GroupTransactionRepository`/`MemberRepository` sang mock `GTransactionService` + `MemberService`                                |

Không đụng: `ReportService` (interface), controller, DTO, repository → **API contract giữ nguyên**.

---

## 2. Lý do thay đổi

**Vì sao `ReportServiceImpl` không nên gọi `GroupTransactionRepository`?**
Theo quy tắc 2.2 trong `spring-boot_struct.md`: lớp này là báo cáo, dữ liệu giao dịch thuộc về `GTransactionService`
(chủ sở hữu entity `GTransaction`). Báo cáo gọi thẳng repository thì:

- bỏ qua mọi quy tắc mà `GTransactionService` đã đóng gói (lọc xoá mềm, trạng thái `CONFIRMED`);
- sau này đổi cách lưu giao dịch thì phải sửa cả chỗ báo cáo.

Gọi qua service thì `ReportServiceImpl` chỉ biết **"cho tôi các giao dịch đã xác nhận"** và **"cho tôi khoảng thời gian
có mặt của thành viên"**, không cần biết dữ liệu nằm ở đâu. Còn tính tổng hay chia phần là việc của `BalanceCalculator`.

**L3 — vì sao không đơn giản là truyền `MemberRes` vào `BalanceCalculator`?**
`BalanceCalculator` chỉ thật sự cần 3 thứ của mỗi thành viên: `userId`, `joinedAt`, `leftAt` (nó tự đóng gói thành
`GroupMemberPeriod`). `MemberRes` **không có `leftAt`**, nên không dựng được khoảng thời gian có mặt. Thêm `leftAt` vào
`MemberRes` thì vỡ constructor 5 tham số ở 4 file test (`MemberControllerTest`, `MemberServiceImplTest`,
`GroupReportServiceTest`…). Nên tôi chọn cho `MemberService` trả thẳng `GroupMemberPeriod`; `BalanceCalculator` nhận danh
sách này qua hàm mới.

**L5 — lỗi logic ẩn: thành viên đã rời nhóm bị mất khỏi báo cáo.**
Code hiện tại lấy thành viên qua `findIdAllMember` (chỉ `ACTIVE`) rồi `findMembers(groupId, ids)`. Hệ quả:

1. Thành viên `LEFT`/`REMOVED` **không có khoảng thời gian có mặt** → khoản chi chung diễn ra lúc họ còn trong nhóm
   **không được chia cho họ**, phần chia của những người còn lại bị đẩy lên.
2. `buildDisplayMembers` có nhánh "hiện người đã rời nếu net ≠ 0" nhưng `findMembers(groupId)` mặc định chỉ trả
   `ACTIVE` + `PENDING`, nên nhánh này **không bao giờ chạy**.

Bản trước refactor lấy `findByGroupIdAndStatusNot(PENDING)` (gồm cả `LEFT`/`REMOVED`) nên không bị. Plan này khôi phục
hành vi đó.

---

## 3. Hướng giải quyết (code xem trước)

### 3.1 `BalanceCalculator` — nơi tập trung phép tính

Tách đôi hàm chính: hàm cũ đổi `List<Member>` → `List<GroupMemberPeriod>` rồi gọi hàm mới. Đặt tên khác
(`calculateBalancesByPeriods`) thay vì overload, vì test hiện có gọi `calculateBalances(List.of(), List.of(), null)` —
overload sẽ làm lời gọi này mơ hồ và vỡ biên dịch.

```java
public static MemberBalances calculateBalances(List<GTransaction> allTxns, List<Member> allMembers, UUID excludeTxnId) {
    List<GroupMemberPeriod> periods = (allMembers == null) ? List.of()
            : allMembers.stream()
            .map(m -> new GroupMemberPeriod(m.getUserId(), m.getJoinedAt(), m.getLeftAt()))
            .toList();
    return calculateBalancesByPeriods(allTxns, periods, excludeTxnId);
}

public static MemberBalances calculateBalancesByPeriods(
        List<GTransaction> allTxns, List<GroupMemberPeriod> memberPeriods, UUID excludeTxnId) {
    // toàn bộ thân hàm cũ, chỉ bỏ đoạn tự đổi Member -> GroupMemberPeriod
}

// tổng tiền các giao dịch đã xác nhận, chưa xóa của một loại trong khoảng [fromTime, toTime)
public static long sumAmountByTypeInPeriod(
        List<GTransaction> allTxns, TransactionType type, Instant fromTime, Instant toTime) {
    if (allTxns == null) return 0L;
    return allTxns.stream()
            .filter(t -> t.getDeletedAt() == null
                    && t.getStatus() == TransactionStatus.CONFIRMED
                    && t.getType() == type
                    && t.getOccurredAt() != null
                    && !t.getOccurredAt().isBefore(fromTime)
                    && t.getOccurredAt().isBefore(toTime))
            .mapToLong(t -> t.getAmount() != null ? t.getAmount() : 0L)
            .sum();
}
```

Điều kiện lọc của `sumAmountByTypeInPeriod` khớp đúng câu JPQL `sumAmountByGroupIdAndTypeAndPeriod` đang dùng (`CONFIRMED`,
chưa xóa, `occurredAt >= from AND < to`).

Hai strategy hoàn tiền (`RefundTransactionStrategy`, `RefundUpdateStrategy`) vẫn gọi hàm cũ → không ảnh hưởng.

### 3.2 `GTransactionService` (+ impl) — chỉ lấy dữ liệu

```java
// các giao dịch đã xác nhận, chưa xóa, xếp theo thời điểm phát sinh tăng dần, kèm danh sách người tham gia
List<GTransaction> findConfirmedTransactions(UUID groupId);
```

```java
@Override
@Transactional(readOnly = true)
public List<GTransaction> findConfirmedTransactions(UUID groupId) {
    return transactionRepository.findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
            groupId, TransactionStatus.CONFIRMED);
}
```

Hàm trả entity `GTransaction` vì `BalanceCalculator` đang làm việc trên entity (và 2 strategy hoàn tiền cũng vậy). Cả hai
đều ở cùng module `group` nên không vi phạm quy tắc 2.2 (chỉ cấm xuyên module).

### 3.3 `MemberService` (+ impl) — chỉ lấy dữ liệu

```java
// khoảng thời gian có mặt của mọi thành viên đã vào nhóm (ACTIVE, LEFT, REMOVED), bỏ PENDING
List<GroupMemberPeriod> findMemberPeriods(UUID groupId);
```

```java
@Override
public List<GroupMemberPeriod> findMemberPeriods(UUID groupId) {
    return memberRepository.findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.PENDING)
            .stream()
            .map(m -> new GroupMemberPeriod(m.getUserId(), m.getJoinedAt(), m.getLeftAt()))
            .toList();
}
```

### 3.4 `ReportServiceImpl`

`getSummary`:

```java
List<GTransaction> confirmedTxns = gTransactionService.findConfirmedTransactions(groupId);
long totalExpense = BalanceCalculator.sumAmountByTypeInPeriod(confirmedTxns, TransactionType.EXPENSE, fromTime, toTime);
long totalContribution = BalanceCalculator.sumAmountByTypeInPeriod(confirmedTxns, TransactionType.CONTRIBUTION, fromTime, toTime);
// ... truyền thẳng vào GroupSummaryReportRes, bỏ kiểm tra null
```

`getBalances`:

```java
MemberBalances mb = BalanceCalculator.calculateBalancesByPeriods(
        gTransactionService.findConfirmedTransactions(groupId),
        memberService.findMemberPeriods(groupId),
        null);

List<MemberRes> activeMembers = memberService.findMembers(groupId, MemberStatus.ACTIVE).stream()
        .sorted(Comparator.comparing(m -> m.userId().toString()))
        .toList();
List<MemberRes> formerMembers = Stream.of(MemberStatus.LEFT, MemberStatus.REMOVED)
        .flatMap(status -> memberService.findMembers(groupId, status).stream())
        .toList();

List<MemberRes> displayMembers = buildDisplayMembers(activeMembers, formerMembers, mb);
```

`buildDisplayMembers(activeMembers, formerMembers, mb)`: ACTIVE luôn hiện, `LEFT`/`REMOVED` chỉ hiện khi `net ≠ 0` (giữ
đúng quy tắc hiện tại, chỉ bỏ vòng `if` lồng nhau).

Bỏ các import không dùng: `GroupTransactionRepository`, `TransactionStatus`. `findIdAllMember` không còn được gọi ở đây.
Thêm import `GTransaction` (entity cùng module) và `BalanceCalculator`.

### 3.5 Test

`GroupReportServiceTest` hiện có chỗ sai sẵn ngay cả trước lần sửa này: stub
`memberService.findMembers(eq(groupId), any(List.class))` trả về `List<Member>` (entity) trong khi kiểu trả về là
`List<MemberRes>`. Sửa:

- thay 2 mock repository bằng `@Mock GTransactionService`; stub `findConfirmedTransactions(groupId)` trả danh sách giao
  dịch và `memberService.findMemberPeriods(groupId)` trả các `GroupMemberPeriod`. Vì phép tính nằm ở `BalanceCalculator`
  và `ReportServiceImpl` gọi thật, **toàn bộ phép kiểm tra số học giữ nguyên** (invariant tổng net = số dư quỹ,
  `neededContribution`, `totalRefunded`);
- stub `memberService.findMembers(groupId, MemberStatus.ACTIVE)` thay cho `findMembers(groupId)` + `findIdAllMember`.

Số test cũ giữ nguyên (3 test), không xoá test nào.

**Test mới đề xuất thêm** (vì đây là lỗi âm thầm, số vẫn "trông hợp lý"):

1. `BalanceCalculatorTest` — `sumAmountByTypeInPeriod`: bỏ giao dịch `PENDING`/`REJECTED`/đã xóa mềm; mốc `fromTime` tính,
   mốc `toTime` không tính; chỉ cộng đúng loại.
2. `BalanceCalculatorTest` — `calculateBalancesByPeriods`: thành viên đã rời vẫn được chia phần chi tiêu diễn ra lúc họ
   còn trong nhóm (chính là L5).
3. `GroupReportServiceTest`: thành viên `LEFT` có `net ≠ 0` **hiện** trong báo cáo; `net = 0` thì **ẩn** (hiện chưa test
   nhánh này).

---

## 4. Câu hỏi cần bạn quyết

| #  | Câu hỏi                                                                                                                                                                                                                                                                                                       | Đề xuất của tôi                                                                                                                       |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Q1 | **Đánh đổi của việc gom tính tổng vào `BalanceCalculator`:** `getSummary` sẽ nạp toàn bộ giao dịch đã xác nhận của nhóm (kèm participants) rồi lọc theo tháng trong bộ nhớ, thay vì để Postgres `SUM` trong 1 câu truy vấn. Nhóm vài nghìn giao dịch thì vẫn ổn, nhóm rất lớn thì chậm hơn. Chấp nhận?          | Chấp nhận theo ý bạn. Phương án thay thế: giữ `sumConfirmedAmount` ở service như một **truy vấn** (không phải phép tính) cho nhanh hơn |
| Q2 | Sau đó câu `sumAmountByGroupIdAndTypeAndPeriod` trong `GroupTransactionRepository` không còn ai gọi. Xóa luôn, hay để lại? (đang có `GroupTransactionRepositoryTest` kiểm nó)                                                                                                                                | **Để lại** — bạn chưa yêu cầu xóa, không tự ý xóa code                                                                                |
| Q3 | `findConfirmedTransactions` trả entity `GTransaction` (cùng module `group`) — chấp nhận? Vì `BalanceCalculator` hiện làm việc trên entity                                                                                                                                                                      | Chấp nhận. Muốn trả DTO thì phải sửa `BalanceCalculator` nhận DTO, ảnh hưởng 2 strategy hoàn tiền và ~20 test — quá rộng             |
| Q4 | `MemberService.findMemberPeriods` trả `GroupMemberPeriod` (record thuần ở `helper/`, không phải entity nên không vi phạm 2.2/2.4) — chấp nhận?                                                                                                                                                                                                                   | Chấp nhận. Lý do chọn thay vì thêm `leftAt` vào `MemberRes`: `MemberRes` là DTO trả ra API, thêm cột chỉ để phục vụ tính toán nội bộ và vỡ constructor ở 4 file test                                                           |
| Q5 | Có thêm 3 test mới ở mục 3.5 không?                                                                                                                                                                                                                                                                           | Có                                                                                                                                    |
| Q6 | `RefundTransactionStrategy` và `RefundUpdateStrategy` cũng đang gọi `MemberRepository` + `GroupTransactionRepository` trực tiếp. Đưa về qua service luôn, hay để riêng?                                                                                                                                       | **Để riêng** — ngoài phạm vi bạn yêu cầu; làm khi bạn bảo                                                                             |
| Q7 | Chạy test: chỉ `GroupReportServiceTest` + `BalanceCalculatorTest` (+ `FundSettlementScenarioTest` vì dùng chung `calculateBalances`) + `MemberServiceImplTest`, không chạy full. Đồng ý?                                                                                                                       | Đồng ý theo `test-rule.md`                                                                                                            |

---

**Khi bạn đánh dấu `[x] PROCESS` (hoặc nhắn "process") tôi mới bắt đầu sửa code.**
