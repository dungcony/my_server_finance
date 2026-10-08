# Quy chuẩn viết Javadoc cho dự án Spring Boot

Tài liệu này định nghĩa tiêu chuẩn tài liệu hóa mã nguồn (Javadoc) trong dự án. Mục tiêu cốt lõi: **Cung cấp ngữ cảnh
nghiệp vụ (Business Context) và các hành vi ngầm (Implicit Behaviors)** mà code không tự thể hiện được, đồng thời tối ưu
hóa khả năng hiển thị trên Popup Hover của IDE/LSP.

## 1. Triết lý chung (Core Principles)

1. **Không lặp lại code (DRY - Don't Repeat Yourself):**
   Không viết lại những gì tên class/method đã thể hiện rõ ràng (ví dụ: `getUserById` không ghi comment "Lấy user theo
   ID").

2. **Tập trung vào "Tại sao" thay vì "Làm thế nào" (Why over How):**
   Giải thích lý do tồn tại của class, quy ước nghiệp vụ ngầm định, hoặc tác động của dữ liệu.

3. **Tận dụng công cụ của IDE:**
   Không liệt kê danh sách method thủ công ở cấp Class. IDE và LSP đã có sẵn tính năng Auto-complete, Code Navigation và
   Outline (`Ctrl + F12` hoặc Document Symbols).

4. **Tránh gắn thông tin dễ thay đổi:**
   Không đưa version file migration, tên ticket Jira, hay chi tiết triển khai tạm thời vào Javadoc vì rất dễ bị lỗi
   thời.

---

## 2. Quy chuẩn cho Class & Interface

### 2.1. Quy tắc

* **NÊN VIẾT:**
    * Trách nhiệm chính của class/interface trong kiến trúc.
    * Bảng DB / Entity liên quan (đặc biệt với Repository).
    * Quy ước nghiệp vụ cốt lõi hoặc các trường hợp biên dữ liệu (Default states, Edge-cases).
    * Entry-point luồng nghiệp vụ chính nếu class có vai trò điều phối phức tạp.

* **KHÔNG NÊN:**
    * Liệt kê danh sách toàn bộ method bằng `{@link}` (gây quá tải popup hover và dễ lỗi thời).
    * Giải thích chi tiết input/output của từng hàm con bên trong.

### 2.2. Ví dụ chuẩn

#### Repository Interface

```java
/**
 * Quản lý truy vấn dữ liệu cho bảng tổng hợp {@code group_member_balances}.
 * <p>
 * <b>Quy ước nghiệp vụ:</b>
 * Bản ghi vắng mặt trong bảng đồng nghĩa thành viên chưa phát sinh giao dịch nào,
 * các chỉ số tài chính được coi là mặc định bằng 0.
 * </p>
 *
 * @see MemberBalance
 * @see MemberBalanceRepositoryCustom
 */
public interface MemberBalanceRepository extends JpaRepository<MemberBalance, MemberBalanceId> {
    // ...
}
```

#### Service Class

```java
/**
 * Điều phối quy trình phân bổ chi phí và thanh toán công nợ giữa các thành viên.
 * <p>
 * Luồng thanh toán yêu cầu khóa bi quan (pessimistic lock) trên các bản ghi số dư
 * liên quan nhằm tránh xung đột dữ liệu khi có nhiều giao dịch đồng thời.
 * </p>
 */
@Service
public class MemberBalanceServiceImpl implements MemberBalanceService {
    // ...
}
```

---

## 3. Quy chuẩn cho Record (DTOs, Commands, Events)

Record đại diện cho dữ liệu bất biến (Immutable Data Carriers). Javadoc ở Record tập trung giải thích ý nghĩa các thuộc
tính, đơn vị đo lường và ràng buộc dữ liệu.

### 3.1. Quy tắc

* Khai báo ngắn gọn mục đích sử dụng (Request DTO, Internal Event, Query Filter).
* Dùng tag `@param` trực tiếp trên class header cho từng field (component).
* Nêu rõ đơn vị (VND, ms, USD...), định dạng đặc biệt (ISO-8601, UUID) hoặc các giá trị cho phép nếu có.

### 3.2. Ví dụ chuẩn

```java
/**
 * Yêu cầu cập nhật số dư thành viên sau khi kết thúc kỳ đối soát.
 *
 * @param groupId     ID định danh của nhóm phát sinh giao dịch
 * @param memberId    ID của thành viên chịu tác động
 * @param deltaAmount Số tiền biến động; giá trị âm là ghi nợ, giá trị dương là ghi có (Đơn vị: VND)
 * @param referenceType Loại nghiệp vụ tham chiếu (VD: {@code EXPENSE}, {@code SETTLEMENT})
 */
public record AdjustBalanceCommand(
                UUID groupId,
                UUID memberId,
                BigDecimal deltaAmount,
                String referenceType
        ) {
}
```

---

## 4. Quy chuẩn cho Method

### 4.1. Cấu trúc chuẩn cho Public / Protected Method (Contract API)

Áp dụng cho các hàm công khai của Service, Repository, Client hoặc Component mà các module khác gọi tới:

1. **Dòng tóm tắt (First line):** Mô tả hành vi chính của hàm.
2. **Tags:**
    * `@param`: Ý nghĩa tham số và các ràng buộc (không null, giới hạn giá trị).
    * `@return`: Dữ liệu trả về (kèm trường hợp trả về `null` hoặc `Optional.empty()`).
    * `@throws`: Liệt kê các Exception nghiệp vụ có thể ném ra và nguyên nhân.

### 4.2. Quy tắc đối với Private Method (Internal Implementation)

Hàm `private` là chi tiết cài đặt nội bộ, phục vụ trực tiếp cho việc chia nhỏ logic (refactor). **Tuyệt đối KHÔNG áp
dụng mẫu Javadoc đầy đủ (`@param`, `@return`, `@throws`) cho hàm private.**

#### Lý do:

1. **Lãng phí chi phí bảo trì:** Hàm private thường xuyên thay đổi tham số, tách gộp logic khi refactor. Viết full tags
   sẽ làm tăng chi phí cập nhật tài liệu vô ích.
2. **Công cụ Javadoc bỏ qua:** Tool sinh HTML Javadoc mặc định không quét hàm `private`.
3. **Ưu tiên Self-documenting Code:** Tên hàm, tham số rõ ràng đã đủ giải thích logic.

#### Phân loại xử lý thực tế:

| Loại hàm `private`                         | Cách viết tài liệu                                                                         | Ví dụ                                                  |
|:-------------------------------------------|:-------------------------------------------------------------------------------------------|:-------------------------------------------------------|
| **Hàm phụ trợ thông thường (90%)**         | **KHÔNG viết comment.** Đặt tên hàm và tham số chuẩn Clean Code.                           | `private boolean isEligibleForDiscount(Member member)` |
| **Hàm có thuật toán / Nghiệp vụ phức tạp** | Viết Javadoc ngắn 1–2 dòng giải thích **LÝ DO (Why)**. **Không dùng `@param`, `@return`**. | Xem ví dụ A bên dưới                                   |
| **Workaround / Fix lỗi tạm thời**          | Dùng comment dòng đơn `//` ngay trước dòng code xử lý.                                     | Xem ví dụ B bên dưới                                   |

#### Ví dụ minh họa:

**Ví dụ A: Hàm private có công thức nghiệp vụ đặc thù (Chỉ ghi mục đích, không ghi tags)**

```java
/**
 * Tính số dư khả dụng sau khi trừ khoản hold tạm thời theo chính sách hoàn tiền 7 ngày.
 * Tham khảo tài liệu nghiệp vụ phần Settlement Rules v2.
 */
private BigDecimal calculateNetAvailableBalance(MemberBalance balance, BigDecimal holdingAmount) {
    return balance.getActualAmount().subtract(holdingAmount);
}
```

**Ví dụ B: Workaround / Xử lý biên (Dùng comment dòng đơn `//`)**

```java
private String sanitizeInput(String raw) {
    // Workaround: Loại bỏ ký tự byte null để tránh lỗi driver PostgreSQL khi insert
    return raw.replace("\u0000", "");
}
```

---

## 5. Quy tắc tài liệu hóa @Transactional & Tác dụng phụ (Side-effects)

Đây là phần **bắt buộc** phải ghi chú khi method can thiệp vào Transaction hoặc có các tác vụ bất đồng bộ / I/O ngoại
vi.

### 5.1. Bảng phân loại hành vi cần ghi chú

| Hành vi                          | Nội dung bắt buộc phải chú thích trong Javadoc                                                                                   |
|:---------------------------------|:---------------------------------------------------------------------------------------------------------------------------------|
| **Locking (`FOR UPDATE`)**       | Nêu rõ loại khóa (Pessimistic/Optimistic), phạm vi record bị lock, và thứ tự lock để cảnh báo nguy cơ **Deadlock**.              |
| **External I/O / 3rd-party API** | Cảnh báo việc gọi HTTP, Email, hoặc SDK bên ngoài trong transaction (nguy cơ nghẽn DB Connection Pool).                          |
| **Propagation đặc biệt**         | Nếu dùng `REQUIRES_NEW`, `MANDATORY` hoặc `NOT_SUPPORTED`, phải giải thích tại sao không dùng `REQUIRED` mặc định.               |
| **Rollback Rules**               | Mặc định Spring chỉ rollback với `RuntimeException`. Nếu có `rollbackFor` với checked exception, phải nêu rõ điều kiện rollback. |
| **Phát tán Event / Cache**       | Ghi rõ tác dụng phụ: phát tán Domain Event (Kafka/RabbitMQ/Spring Event) hoặc xóa cache qua `@CacheEvict`.                       |

### 5.2. Ví dụ mẫu cho các trường hợp điển hình

#### Trường hợp 1: Hàm giữ Khóa bi quan (Pessimistic Lock / `FOR UPDATE`)

```java
/**
 * Khóa và tải danh sách số dư của các thành viên trong nhóm để chuẩn bị cập nhật.
 * <p>
 * <b>Cảnh báo Locking & Deadlock:</b>
 * <ul>
 *   <li>Hàm kích hoạt khóa bi quan ({@code SELECT ... FOR UPDATE}) trên bảng {@code group_member_balances}.</li>
 *   <li>Để ngăn ngừa Deadlock, danh sách {@code userIds} được tự động sắp xếp theo thứ tự tăng dần trước khi truy vấn.</li>
 *   <li><b>Lưu ý:</b> Giữ transaction càng ngắn càng tốt; tuyệt đối không gọi I/O mạng hoặc API ngoài sau khi gọi hàm này.</li>
 * </ul>
 * </p>
 *
 * @param groupId ID của nhóm cần truy vấn
 * @param userIds Danh sách ID thành viên cần lấy số dư; không được rỗng
 * @return Đối tượng {@link MemberBalances} chứa dữ liệu đã được khóa
 * @throws IllegalArgumentException Nếu {@code userIds} rỗng
 * @throws EntityNotFoundException  Nếu nhóm không tồn tại
 */
@Transactional
MemberBalances getBalancesForUpdate(UUID groupId, Collection<UUID> userIds);
```

#### Trường hợp 2: Transaction độc lập (`REQUIRES_NEW`) & Tác dụng phụ

```java
/**
 * Ghi nhận log giao dịch kiểm toán vào bảng độc lập.
 * <p>
 * <b>Cơ chế Transaction:</b>
 * Hàm sử dụng {@code Propagation.REQUIRES_NEW} để mở một transaction riêng biệt.
 * Log này sẽ được lưu cố định vào DB ngay cả khi luồng nghiệp vụ cha bị rollback.
 * </p>
 *
 * @param auditPayload Dữ liệu kiểm toán cần lưu vết
 */
@Transactional(propagation = Propagation.REQUIRES_NEW)
void logAuditTrail(AuditPayload auditPayload);
```

#### Trường hợp 3: Hàm có phát tán Event và Xóa Cache

```java
/**
 * Duyệt khoản chi tiêu và phân bổ lại nợ trong nhóm.
 * <p>
 * <b>Tác dụng phụ (Side-effects):</b>
 * <ul>
 *   <li>Xóa cache trạng thái nhóm: {@code @CacheEvict(cacheNames = "group_summary", key = "#groupId")}.</li>
 *   <li>Phát tán sự kiện {@link ExpenseApprovedEvent} sau khi transaction commit thành công.</li>
 * </ul>
 * </p>
 *
 * @param expenseId ID khoản chi tiêu cần duyệt
 * @throws ExpenseNotFoundException Nếu không tìm thấy khoản chi tiêu
 * @throws IllegalStateException    Nếu khoản chi tiêu đã được duyệt trước đó
 */
@Transactional
@CacheEvict(cacheNames = "group_summary", key = "#groupId")
void approveExpense(UUID expenseId);
```

---

## 6. Phân định giữa Javadoc và Swagger / OpenAPI

| Đặc tính                | Javadoc                                                  | Swagger / OpenAPI Annotations                     |
|:------------------------|:---------------------------------------------------------|:--------------------------------------------------|
| **Đối tượng đọc**       | Lập trình viên Backend bảo trì mã nguồn                  | Frontend, Mobile dev, External API Client         |
| **Vị trí áp dụng**      | Service, Repository, Utility, Config, Internal Component | Controller, Request/Response DTO công khai        |
| **Annotations sử dụng** | `@param`, `@return`, `@throws`, `@see`                   | `@Operation`, `@ApiResponse`, `@Schema`           |
| **Nguyên tắc**          | Tránh viết dài dòng ở Controller nếu đã có Swagger       | Không thay thế logic nghiệp vụ nội bộ của Service |

---

## 7. Bảng kiểm tra trước khi tạo Pull Request (PR Checklist)

- [ ] Header của Class/Repository có giải thích ý nghĩa bảng DB hoặc quy ước nghiệp vụ quan trọng không?
- [ ] Đã loại bỏ việc liệt kê danh sách method thủ công bằng `{@link}` ở đầu Class chưa?
- [ ] Đã loại bỏ các thông tin dễ lỗi thời (version migration, ID ticket...) khỏi comment chưa?
- [ ] Các Record có đầy đủ tag `@param` giải thích các field và đơn vị đo lường (VND, ms...) chưa?
- [ ] Các method `public`/`protected` có đầy đủ `@param`, `@return`, `@throws` chưa?
- [ ] **Các method `private` có bị viết thừa thãi (`@param`, `@return`) không? (Chỉ ghi tóm tắt 1 dòng nếu có thuật toán
  phức tạp, còn lại ưu tiên tên hàm tự giải thích).**
- [ ] Các method có `@Transactional` kết hợp Lock (`FOR UPDATE`) có cảnh báo deadlock và thời gian giữ kết nối chưa?
- [ ] Các method có tác dụng phụ (xóa cache, phát tán event, `REQUIRES_NEW`) đã được nêu rõ ràng chưa?
- [ ] Mọi Exception nghiệp vụ được `throw` đã được ghi chú qua `@throws` chưa?