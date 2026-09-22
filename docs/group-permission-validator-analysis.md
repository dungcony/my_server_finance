# Phân tích kiến trúc `GroupPermissionValidator`

## 1. Bối cảnh

Trong module `group`, nhiều Service Implementation đang lặp lại các logic kiểm tra quyền và tư cách thành viên, ví dụ:

- Kiểm tra user có phải thành viên `ACTIVE` của group hay không.
- Kiểm tra user có phải `OWNER` đang `ACTIVE` hay không.
- Kiểm tra user có phải `OWNER` hoặc người đang giữ ví hay không.
- Khi không đủ quyền thì ném `BusinessException` với `ErrorCode` tương ứng.

Việc copy-paste các logic này giữa nhiều service làm tăng duplicated code và khiến việc thay đổi quy tắc phân quyền sau
này phải sửa ở nhiều nơi.

Giải pháp được đề xuất là tập trung các rule này vào:

```text
group/validator/GroupPermissionValidator.java
```

---

## 2. `Validator` có nên gọi `Repository` không?

### Kết luận

**Có. `GroupPermissionValidator` gọi trực tiếp `GroupMemberRepository` là hợp lý.**

Lý do là validator này không chỉ kiểm tra dữ liệu đầu vào đơn giản, mà đang thực hiện **business/domain validation dựa
trên dữ liệu trong database**.

Ví dụ:

```java
public void verifyActiveMember(UUID groupId, UUID userId) {
    boolean isMember =
            groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    groupId,
                    userId,
                    MemberStatus.ACTIVE
            );

    if (!isMember) {
        throw new BusinessException(
                ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER
        );
    }
}
```

Validator cần biết trạng thái thành viên thực tế trong database để quyết định user có được phép tiếp tục hay không.

Luồng xử lý là:

```text
Service
   |
   v
GroupPermissionValidator
   |
   v
GroupMemberRepository
   |
   v
Database
   |
   v
Có phải ACTIVE member?
   |
   +---- Có ----> tiếp tục
   |
   +---- Không --> BusinessException
```

Đây là dependency trực tiếp và dễ hiểu.

---

## 3. Phân biệt Input Validation và Business Validation

Không nên hiểu rằng mọi `Validator` đều không được phép truy cập Repository.

### 3.1. Input validation

Đây là validation dữ liệu đầu vào, thường không cần database.

Ví dụ:

```java

@NotNull
private UUID groupId;
```

hoặc:

```java
if(amount <=0){
        throw new

BusinessException(...);
}
```

Các validation dạng này có thể thực hiện bằng Bean Validation hoặc logic thuần túy.

### 3.2. Business/domain validation

Đây là validation dựa trên trạng thái hoặc quan hệ của domain.

Ví dụ:

```text
User có thuộc group không?
User có ACTIVE không?
User có phải OWNER không?
User có quyền thao tác với wallet không?
```

Các câu hỏi này cần dữ liệu domain từ database.

Do đó việc:

```text
Validator -> Repository
```

là hợp lý.

---

## 4. Phân tích `GroupPermissionValidator`

Cấu trúc hiện tại:

```java

@Component
@RequiredArgsConstructor
public class GroupPermissionValidator {

    private final GroupMemberRepository groupMemberRepository;
}
```

### 4.1. `verifyActiveMember`

```java
public void verifyActiveMember(UUID groupId, UUID userId) {
    boolean isMember =
            groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    groupId,
                    userId,
                    MemberStatus.ACTIVE
            );

    if (!isMember) {
        throw new BusinessException(
                ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER
        );
    }
}
```

Responsibility rõ ràng:

> Xác nhận user là thành viên ACTIVE của group.

Nếu không thỏa điều kiện, method chịu trách nhiệm phát ra lỗi nghiệp vụ tương ứng.

Đây là một business validation phù hợp với `GroupPermissionValidator`.

---

## 5. `verifyOwnerRole` và `isOwner`

Hai method có quan hệ:

```java
public void verifyOwnerRole(UUID groupId, UUID userId) {
    if (!isOwner(groupId, userId)) {
        throw new BusinessException(
                ErrorCode.FORBIDDEN_OWNER_REQUIRED
        );
    }
}
```

và:

```java
public boolean isOwner(UUID groupId, UUID userId) {
    return groupMemberRepository
            .existsByGroupIdAndUserIdAndRoleAndStatus(
                    groupId,
                    userId,
                    GroupRole.OWNER,
                    MemberStatus.ACTIVE
            );
}
```

Thiết kế này có ưu điểm là tách:

- `isOwner()` → kiểm tra điều kiện và trả `boolean`.
- `verifyOwnerRole()` → enforce business rule và ném exception.

Nếu các service khác thực sự cần kiểm tra dạng:

```java
if(groupPermissionValidator.isOwner(groupId, userId)){
        ...
        }
```

thì `isOwner()` nên để `public`.

Nếu `isOwner()` chỉ được dùng nội bộ bởi `verifyOwnerRole()` và không có consumer khác, có thể đổi thành:

```java
private boolean isOwner(UUID groupId, UUID userId)
```

để giảm public API của validator.

---

## 6. `verifyOwnerOrTreasurer`

Rule:

```java
boolean isTreasurer =
        heldByUserId != null && heldByUserId.equals(userId);

boolean isOwner = isOwner(groupId, userId);

if(!isOwner &&!isTreasurer){
        throw new

BusinessException(
        ErrorCode.FORBIDDEN_TREASURER_REQUIRED
        );
}
```

Rule nghiệp vụ ở đây là:

```text
                 +--> OWNER --------+
User được phép --|                  |--> Cho phép
                 +--> Wallet holder-+
```

Trong method này:

- `isOwner` cần repository vì phải kiểm tra membership/role trong database.
- `isTreasurer` chỉ là phép so sánh UUID, không cần repository.

Việc hai điều kiện này nằm chung trong validator vẫn hợp lý vì chúng cùng phục vụ một business rule về permission.

---

## 7. Tại sao không dùng `GroupMemberHelper`?

Có thể đặt các method này vào:

```text
group/helper/GroupMemberHelper.java
```

nhưng tên `Helper` không thể hiện rõ responsibility.

Ví dụ sau này class có:

```text
verifyActiveMember()
verifyOwnerRole()
verifyOwnerOrAdminRole()
verifyOwnerOrTreasurer()
verifyMemberCanEdit()
verifyMemberCanDelete()
```

thì class thực chất đang trở thành nơi chứa toàn bộ authorization/permission rules của group.

Tên:

```text
GroupPermissionValidator
```

mô tả chính xác hơn responsibility:

> Validate quyền của user khi thao tác trong group.

---

## 8. Dependency direction

Kiến trúc đề xuất:

```text
GroupService
      |
      v
GroupPermissionValidator
      |
      v
GroupMemberRepository
      |
      v
Database
```

Các service khác cũng sử dụng cùng validator:

```text
GroupWalletService --------GroupTransactionService ----> GroupPermissionValidator
GroupReportService --------/
GroupService -------------/
                              |
                              v
                    GroupMemberRepository
```

Điểm quan trọng là các service không còn phải tự copy:

```java
groupMemberRepository.existsBy...
```

và tự tạo `BusinessException` cho cùng một rule.

---

## 9. Lợi ích

### Giảm duplicated code

Thay vì nhiều service có cùng logic:

```java
boolean isOwner = ...
        if(!isOwner){
        throw...
        }
```

chỉ còn:

```java
groupPermissionValidator.verifyOwnerRole(groupId, userId);
```

### Tập trung business rule

Nếu sau này rule OWNER thay đổi, ví dụ cần thêm điều kiện khác, chỉ cần sửa một nơi.

### Nhất quán ErrorCode

Các service không tự chọn hoặc copy-paste sai `ErrorCode`.

### Dễ đọc Service

Service tập trung vào use case:

```java
groupPermissionValidator.verifyOwnerRole(groupId, userId);

// xử lý nghiệp vụ chính
```

thay vì bị trộn với implementation của permission check.

### Dễ test

Có thể test riêng:

```text
GroupPermissionValidatorTest
```

với các trường hợp:

- ACTIVE member → pass.
- Không phải member → `FORBIDDEN_NOT_GROUP_MEMBER`.
- OWNER → pass.
- Không phải OWNER → `FORBIDDEN_OWNER_REQUIRED`.
- OWNER hoặc wallet holder → pass.
- Không phải cả hai → `FORBIDDEN_TREASURER_REQUIRED`.

---

## 10. Những điều nên tránh

### Không nên để Validator gọi Service khác nếu không cần

Ví dụ không nên tạo dependency kiểu:

```text
GroupPermissionValidator
        |
        v
GroupService
        |
        v
GroupMemberRepository
```

nếu có thể gọi repository trực tiếp.

Điều này làm dependency chain dài và dễ tạo coupling hoặc circular dependency.

Cấu trúc đơn giản hơn:

```text
GroupPermissionValidator
        |
        v
GroupMemberRepository
```

### Không nên biến Validator thành một "God Class"

Chỉ đưa vào các rule liên quan đến **permission/membership của group**.

Không nên nhét các nghiệp vụ không liên quan như:

```text
calculateBalance()
createTransaction()
calculateReport()
sendNotification()
```

---

## 11. Cấu trúc package đề xuất

```text
group/
├── controller/
├── service/
│   ├── GroupService.java
│   ├── GroupWalletService.java
│   ├── GroupTransactionService.java
│   └── GroupReportService.java
│
├── repository/
│   └── GroupMemberRepository.java
│
├── validator/
│   └── GroupPermissionValidator.java
│
├── helper/
│   └── GroupBalanceCalculator.java
│
├── entity/
├── enums/
└── dto/
```

Trong đó:

```text
validator/
    -> business validation về permission/membership

helper/
    -> các utility/calculation có responsibility khác

service/
    -> orchestration/use case chính

repository/
    -> truy cập persistence
```

---

## 12. Khuyến nghị cuối cùng

Với code hiện tại, nên giữ:

```java

@Component
@RequiredArgsConstructor
public class GroupPermissionValidator {

    private final GroupMemberRepository groupMemberRepository;
}
```

và cho phép validator gọi trực tiếp repository.

Các method chính:

```java
verifyActiveMember(...)

verifyOwnerRole(...)

verifyOwnerOrTreasurer(...)
```

là những method phù hợp để đặt tại đây.

`isOwner(...)` nên:

- `public` nếu các service khác cần sử dụng kết quả boolean.
- `private` nếu chỉ phục vụ nội bộ cho `verifyOwnerRole()` và `verifyOwnerOrTreasurer()`.

### Kết luận

```text
Validator -> Repository
```

**là hợp lý trong trường hợp này**, vì đây là business/domain validation dựa trên dữ liệu thành viên trong database.

Điều quan trọng không phải là "Validator có được gọi Repository hay không", mà là responsibility phải rõ:

```text
GroupPermissionValidator
        |
        +-- kiểm tra membership
        +-- kiểm tra role
        +-- kiểm tra permission
        |
        +-- Repository để lấy trạng thái domain
        |
        +-- BusinessException nếu rule không thỏa
```

Đây là hướng phù hợp để loại bỏ duplicated permission-checking code giữa các Group Service.
