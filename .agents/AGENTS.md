# Finance AI Server - Workspace Rules

## 1. Project Overview

- I am a backend Java application.
- I am responsible for writing the server code.
- I use Java 17.
- I use Spring Boot 3.4.13.
- I use Maven and Spring Boot.
- Ưu tiên nguyên tắc SOLID và Clean Code.
- Ưu tiên tách các đoạn code duplication dài hơn 2 dòng thành các private method.

## 2. Module Architecture Rules

> Quy tắc bắt buộc khi viết code trong dự án. Áp dụng cho mọi module (`user`, `order`, `payment`, `budget`, ...).

### 2.1 Cấu trúc thư mục bắt buộc

Mỗi module PHẢI theo đúng cấu trúc sau, không thêm/bớt layer tuỳ tiện:

```text
module/
├── common/
│   ├── config/                # Cấu hình dùng chung toàn hệ thống
│   ├── exception/             # BusinessException.java, GlobalExceptionHandler.java, ErrorCode.java
│   ├── response/              # ApiResponse.java, ErrorResponse.java, PageMeta.java
│   ├── security/              # SecurityConfig, JwtFilter, SecurityContextUtil
│   ├── logging/               # LoggingFilter, LogMasker, RequestId MDC
│   ├── util/                  # Pure utility functions
│   └── constant/
└── <domain>/                  # user, order, payment, budget, ...
    ├── controller/
    ├── service/
    │   ├── <Domain>Service.java       # interface — PUBLIC API
    │   └── impl/<Domain>ServiceImpl.java
    ├── repository/
    ├── entity/
    ├── enums/
    ├── events/                 # chỉ tạo khi thực sự cần (Spring ApplicationEvent)
    ├── validator/              # Nghiệp vụ validate phức tạp/cần DB
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/                 # MapStruct interface
    ├── helper/                 # Chỉ tạo khi thực sự có logic nghiệp vụ phức tạp cần tái sử dụng
    ├── config/                 # Chỉ tạo khi domain có cấu hình nghiệp vụ riêng (@ConfigurationProperties)
    └── exception/              # Exception riêng của domain (kế thừa BusinessException)
```

**KHÔNG ĐƯỢC:**

- Đặt entity/repository/DTO ngoài module tương ứng.
- Tạo layer mới (VD: `manager/`, `facade/`) mà không thống nhất convention trước.

---

### 2.2 Cross-module Dependency & Architecture Enforcement

**PHẢI:**

- Module A cần dữ liệu của module B → gọi qua `<B>Service` interface (public API), không import `Repository` hoặc
  `Entity` của B.
- Trả về DTO (`XxxSummaryResponse`, `XxxDetailResponse`), không trả `Entity` khi expose ra ngoài module.
- **Enforcement:** Quy tắc phân tách module được kiểm soát tự động qua **ArchUnit test suite** trong `src/test/`. Bất kỳ
  vi phạm import chéo repository/entity nào sẽ làm fail build `mvn test`.

**KHÔNG ĐƯỢC:**

- Import `xxx.repository.*` hoặc `xxx.entity.*` từ module khác.
- Tạo circular dependency (vòng lặp phụ thuộc) giữa các module.

---

### 2.3 Entity Rules & Relationship Xuyên Module

**PHẢI:**

- **Entity Relationship Xuyên Module (Dùng ID thuần):**
  - Lưu `UUID <domain>Id` (hoặc `Long <domain>Id`) thay vì quan hệ ORM trực tiếp.
  - Tránh coupling ở tầng JPA/DB giữa 2 module.
  - Tránh `LazyInitializationException` khi entity được truy xuất ngoài transaction gốc.
  - Ép buộc mọi truy cập dữ liệu liên module phải đi qua Service API.
- **Enum Persistence:**
  - Mọi thuộc tính enum trong Entity BẮT BUỘC phải dùng `@Enumerated(EnumType.STRING)`.
  - Luôn lưu trữ dưới dạng chuỗi có ý nghĩa rõ ràng trong database.

**KHÔNG ĐƯỢC:**

- Dùng `@ManyToOne` / `@OneToMany` xuyên module.
- Dùng `EnumType.ORDINAL` hoặc để JPA mặc định (ORDINAL). Khi thứ tự enum bị thay đổi hoặc thêm mới, dữ liệu cũ trong DB
  sẽ bị ánh xạ sai lệch mà không có cảnh báo.

---

### 2.4 Response Contract & DTO

**PHẢI:**

- **Response Wrapper thống nhất (`ApiResponse<T>`):** Mọi Controller thành công PHẢI bọc kết quả qua `ApiResponse<T>`:
  - Khai báo: `public record ApiResponse<T>(boolean success, T data, String msg)`
  - Helper methods: `ApiResponse.of(data)` hoặc `ApiResponse.of(data, msg)`.
  - Format JSON:
    ```json
    {
      "success": true,
      "data": {},
      "msg": ""
    }
    ```

- **Phân trang (Pagination):** Kết quả phân trang phải trả về dữ liệu kèm metadata phân trang chuẩn (`PageMeta`):
  - Khai báo metadata: `public record PageMeta(int page, int pageSize, long totalItems, int totalPages)`
  - Format JSON phân trang:
    ```json
    {
      "success": true,
      "data": {
        "items": [],
        "meta": {
          "page": 1,
          "page_size": 20,
          "total_items": 100,
          "total_pages": 5
        }
      },
      "msg": ""
    }
    ```

- **Error Response Wrapper (`ErrorResponse`):** Định dạng trả về đồng nhất khi có lỗi từ `GlobalExceptionHandler`:
  - Khai báo: `public record ErrorResponse(boolean success, ErrorBody error)` với
    `ErrorBody(String code, String message, List<FieldError> fields, Object detail)`
  - Format JSON:
    ```json
    {
      "success": false,
      "error": {
        "code": "RESOURCE_NOT_FOUND",
        "message": "Không tìm thấy dữ liệu",
        "fields": [
          { "field": "email", "message": "Email không đúng định dạng" }
        ],
        "detail": null
      }
    }
    ```

- **Phân loại DTO:**
  - Tách thư mục `dto/request/` và `dto/response/`.
  - Tách DTO theo mục đích rõ ràng: `<Domain>CreateRequest`, `<Domain>UpdateRequest`, `<Domain>SummaryResponse`,
    `<Domain>DetailResponse`.

- **Quy ước Naming Case (JSON Body & Query Param):**
  - **Trong Java code:** Toàn bộ thuộc tính DTO, Entity và Record khai báo theo chuẩn `camelCase` của Java (VD:
    `pageSize`, `totalItems`, `userId`).
  - **Trong HTTP API (JSON Body & Query Param):** Thống nhất dùng `snake_case` cho cả request/response payload
    (`page_size`, `total_items`) và query param (`?page_size=20`).
  - **Cơ chế chuyển đổi toàn cục:** Cấu hình qua Spring Boot (`spring.jackson.property-naming-strategy: SNAKE_CASE`
    trong `application.yml`). Jackson tự động ánh xạ giữa Java camelCase và JSON snake_case, không cần gắn
    `@JsonProperty` thủ công (trừ trường hợp đặc thù).

**KHÔNG ĐƯỢC:**

- Trả trực tiếp `Entity` ở Controller hoặc trong response DTO.
- Tự chế cấu trúc response khác (như trả trần object hoặc tự bọc `Map<String, Object>`).
- Dùng chung 1 DTO cho cả Create và Update nếu danh sách field khác nhau.

---

### 2.5 Transaction Management (`@Transactional`)

**PHẢI:**

- **Vị trí duy nhất:** Chỉ đặt `@Transactional` trên các **public method** tại tầng `*ServiceImpl`.
- **Phân tách đọc/ghi:**
  - Method truy vấn / đọc dữ liệu: `@Transactional(readOnly = true)` để tối ưu tài nguyên và bỏ qua dirty checking của
    Hibernate.
  - Method ghi / thay đổi dữ liệu: `@Transactional` (sử dụng propagation `REQUIRED` mặc định).
- **Tránh Self-Invocation:** Không gọi trực tiếp `this.methodCoTransactional()` trong cùng một class vì Spring AOP proxy
  sẽ bị bypass, khiến transaction không được kích hoạt.

**KHÔNG ĐƯỢC:**

- Đặt `@Transactional` ở tầng Controller (gây giữ DB connection quá lâu).
- Đặt `@Transactional` ở tầng Repository (Repository đã được Spring Data JPA quản lý).
- Đặt `@Transactional` ở class-level trong ServiceImpl (dễ quên `readOnly = true` cho các method đọc).

---

### 2.6 Validation Flow — Phân Định Ranh Giới Rõ Ràng

**PHẢI:**

- **Tầng 1 - Format / Field-level (Tại DTO):**
  - Dùng Jakarta Bean Validation annotation chuẩn (`@NotNull`, `@NotBlank`, `@Size`, `@Positive`, `@Pattern`,
    `@Email`...).
  - Controller khai báo `@Valid` trước `@RequestBody`.
- **Tầng 2 - Domain / State-level (Tại `<domain>/validator/`):**
  - Áp dụng khi cần kiểm tra logic nghiệp vụ phức tạp: truy vấn DB kiểm tra tồn tại/trùng lặp, kiểm tra hạn mức
    (quota), hoặc logic phụ thuộc giữa nhiều trường/nhiều entity.
  - Validator ném exception cụ thể kế thừa từ `BusinessException`.

**KHÔNG ĐƯỢC:**

- Viết custom annotation để truy vấn database ở tầng DTO.
- Bỏ qua Bean Validation ở DTO và đẩy toàn bộ việc kiểm tra null/rỗng xuống Service.

---

### 2.7 Mapper (MapStruct)

**PHẢI:**

- Sử dụng **MapStruct** cho việc chuyển đổi giữa Entity và DTO.
- **Bắt lỗi thiếu field tại compile-time:** Luôn cấu hình `unmappedTargetPolicy = ReportingPolicy.ERROR` trên
  `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)` (hoặc kế thừa cấu hình chung qua
  `@MapperConfig`). Khi Entity hoặc DTO thêm/đổi tên field mà Mapper chưa cập nhật, build sẽ FAIL ngay lập tức thay vì
  âm thầm bỏ sót (gây null dữ liệu).
- **Khai báo bỏ qua tường minh:** Đối với các target field cố ý không map, BẮT BUỘC phải chỉ định rõ qua annotation
  `@Mapping(target = "fieldName", ignore = true)`.
- Logic mapping phức tạp hoặc tính toán thêm trường hiển thị thì viết `default method` ngay trong interface Mapper.

**KHÔNG ĐƯỢC:**

- Viết thủ công các hàm mapper bằng tay (set từng field lặp đi lặp lại) nếu là mapping 1-1 thông thường.
- Cấu hình `unmappedTargetPolicy = ReportingPolicy.IGNORE` làm mất cơ chế cảnh báo biên dịch.

---

### 2.8 Controller & RESTful URL Convention

**PHẢI:**

- **Tên Resource:** Dùng danh từ số nhiều ở dạng `kebab-case` (VD: `/users`, `/budgets`, `/wallets`).
- **Path Variable Style:**
  - Resource đơn cấp: Dùng `{id}` (VD: `/budgets/{id}`, `/wallets/{id}`).
  - Nested resource (phụ thuộc cha-con): Dùng `{parentId}` cho resource cha và `{id}` cho resource con (VD:
    `/wallets/{walletId}/transactions/{id}`).
  - Giới hạn lồng nhau: **Tối đa 2 cấp** (VD: `/parents/{parentId}/children/{id}`). Tránh lồng quá 2 cấp
    (Anti-pattern: `/a/{aId}/b/{bId}/c/{cId}/d`).
- **Query Parameter Style:**
  - Dùng `snake_case` cho toàn bộ query param: `?is_active=true`, `?from_date=2026-01-01`,
    `?sort_by=created_at&sort_dir=desc`.
  - Phân trang chuẩn: `?page=1&page_size=20`.
- **HTTP Methods chuẩn:**
  - `GET`: Lấy dữ liệu (idempotent, safe).
  - `POST`: Tạo mới tài nguyên.
  - `PUT` / `PATCH`: Cập nhật tài nguyên (toàn phần / một phần).
  - `DELETE`: Xóa hoặc đổi trạng thái vô hiệu hóa.
- **Context Path & Versioning:** Lưu ý context-path / versioning (VD: `/v1`) đã được cấu hình tại cấu hình ứng dụng
  (application config). Do đó `@RequestMapping` ở Controller chỉ định nghĩa endpoint tài nguyên (VD:
  `@RequestMapping("/budgets")`), KHÔNG lặp lại tiền tố `/v1` hay `/api/v1`.

---

### 2.9 Security & Authorization Boundary

**PHẢI:**

- **Chặn quyền tại Controller (Role/Permission):** Sử dụng `@PreAuthorize("hasAuthority('...')")` hoặc
  `@PreAuthorize("hasRole('...')")` tại Controller để kiểm soát quyền truy cập hệ thống.
- **Xác thực quyền sở hữu tài nguyên tại Service (Chống IDOR):**
  - `@PreAuthorize` chỉ bảo vệ vai trò chung, **KHÔNG** bảo vệ được dữ liệu cá nhân nếu user A gọi API với ID tài
    nguyên của user B (Insecure Direct Object References).
  - Tầng Service **BẮT BUỘC** phải xác thực tính sở hữu tài nguyên (Resource Ownership):
    - _Cách 1 (Ưu tiên):_ Truy vấn qua Repository với điều kiện kép `findByIdAndUserId(id, userId)` (hoặc
      `findByKeyAndTenantId(...)`).
    - _Cách 2:_ Kiểm tra tường minh `if (!resource.getUserId().equals(currentUserId))` trước khi xử lý nghiệp vụ
      đọc/ghi.
    - Ném exception phù hợp (thường là `ResourceNotFoundException` để tránh rò rỉ sự tồn tại của dữ liệu người
      khác).
- **Truyền danh tính chuẩn:** Controller lấy danh tính người dùng qua Security Context
  (`SecurityContextUtil.currentUserId()`) và truyền ID thuần (`UUID userId`) xuống Service.

**KHÔNG ĐƯỢC:**

- Chỉ dùng `findById(id)` ở Service rồi trả dữ liệu cho user mà bỏ qua bước kiểm tra quyền sở hữu (`userId`).
- Truyền trực tiếp token JWT thô hoặc `HttpServletRequest` vào tầng Service.
- Tự viết logic giải mã hoặc parse token thủ công trong Service.

---

### 2.10 Logging & Tracing

**PHẢI:**

- Sử dụng annotation `@Slf4j` của Lombok cho mọi class cần ghi log.
- Tận dụng `MDC` (Mapped Diagnostic Context) với khóa `requestId` (hoặc `X-Request-Id`) để theo dõi trace log xuyên suốt
  từng request.
- **Phân loại level log chuẩn:**
  - `INFO`: Ghi nhận các mốc nghiệp vụ chính (bắt đầu job, hoàn thành transaction...).
  - `WARN`: Cảnh báo logic, vi phạm validation, lỗi client 4xx.
  - `ERROR`: Lỗi hệ thống 5xx, bắt buộc log kèm theo Throwable / Exception stack trace
    (`log.error("Message...", ex)`).
- Che giấu (mask) dữ liệu nhạy cảm (mật khẩu, access/refresh token, OTP) trước khi log.

**KHÔNG ĐƯỢC:**

- Dùng `System.out.println` hoặc `e.printStackTrace()`.

---

### 2.11 Config Management

**PHẢI:**

- Sử dụng `@ConfigurationProperties` đóng gói theo record/POJO có type-safe và validate cho các nhóm cấu hình nghiệp vụ.
- Tách cấu hình môi trường rõ ràng (`application.yml`, `application-dev.yml`, `application-prod.yml`).
- Cấu hình hạ tầng/dùng chung đặt ở `common/config/`, cấu hình đặc thù của domain đặt ở `<domain>/config/`.

**KHÔNG ĐƯỢC:**

- Rải rác quá nhiều annotation `@Value("${...}")` trực tiếp trong code business.

---

### 2.12 Exception Handling

**PHẢI:**

- Mọi exception nghiệp vụ PHẢI extend `BusinessException` (ở `common/exception/`).
- Mỗi domain định nghĩa exception riêng trong `<domain>/exception/`.
- Mỗi exception gắn với 1 `ErrorCode` có sẵn `HttpStatus` và message mẫu.
- Toàn bộ exception được bắt và format tập trung tại `GlobalExceptionHandler`.

**KHÔNG ĐƯỢC:**

- Throw `RuntimeException` trực tiếp trong service mà không qua `BusinessException`.
- Viết `@ExceptionHandler` riêng lẻ trong từng controller.

---

### 2.13 Testing Strategy & Architecture Enforcement

**PHẢI:**

- **Cấu trúc Package Mirror:** Thư mục test trong `src/test/java/` PHẢI mirror 1-1 với package cấu trúc trong
  `src/main/java/` (VD: `com.datn.financeapp.budget.service.impl.BudgetServiceImplTest`).
- **Phân loại Test rõ ràng:**
  - **Unit Test (`*Test.java` / `*ServiceImplTest.java`):**
    - Áp dụng cho: ServiceImpl, Helper, Validator, Mapper, pure logic.
    - Công nghệ: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
    - Nguyên tắc: Mock toàn bộ dependency (Repository, MailService...), kiểm thử biên, validation, exception logic.
      Chạy cực nhanh, **KHÔNG** bật Spring Context.
  - **Integration Test (`*IntegrationTest.java`):**
    - Áp dụng cho: Controller API endpoints, Idempotency, Filter, Transaction Rollback, End-to-End flows.
    - Công nghệ: `@SpringBootTest`, `MockMvc`, kết hợp `@Testcontainers` (PostgreSQL / Redis container độc lập).
    - Nguyên tắc: Kiểm thử hành vi thực tế từ HTTP status, JSON payload, DB constraints và Security Access.
  - **Repository Test (`*RepositoryTest.java`):**
    - Áp dụng cho: Custom SQL query (`@Query`), complex projection, soft-delete query, dynamic specifications.
    - Công nghệ: `@DataJpaTest` hoặc Testcontainers.
- **ArchUnit Test Suite:**
  - Vị trí: Đặt tại package kiến trúc trong `src/test/java/` (VD: `com.datn.financeapp.architecture`).
  - Mục đích: Tự động hóa kiểm tra các rule kiến trúc trong mỗi lần build `mvn test`:
    - Không import chéo Repository/Entity giữa các module (Rule 2.2).
    - Không tạo vòng lặp phụ thuộc (circular dependency) giữa các package.
    - Không dùng quan hệ ORM trực tiếp xuyên module (Rule 2.3).
    - Bắt buộc `@Enumerated(EnumType.STRING)` trên mọi thuộc tính enum trong Entity.

**KHÔNG ĐƯỢC:**

- Lạm dụng `@SpringBootTest` cho các bài test logic đơn giản (gây chậm build nghiêm trọng).
- Viết test phụ thuộc vào database local cố định (phải dùng Testcontainers để đảm bảo tính cô lập và tái lập được).
- Bỏ qua assert kết quả và chỉ gọi method cho đủ coverage.

---

### 2.14 Naming Convention

- **Entity:** Danh từ số ít (`User`, `Order`, `Budget`).
- **Request DTO:** `<Domain><Action>Request` (`UserCreateRequest`, `BudgetUpdateRequest`).
- **Response DTO:** `<Domain>Response`, `<Domain>DetailResponse`, `<Domain>SummaryResponse`.
- **Exception:** `<Domain><LỗiGì>Exception` (`UserNotFoundException`, `BudgetLimitExceededException`).
- **Service Interface:** Đặt tên theo hành vi nghiệp vụ (`registerUser()`, `createBudget()`).
- **Service Impl:** `<Domain>ServiceImpl`.
- **Mapper:** `<Domain>Mapper`.
- **Validator:** `<Domain>Validator`.
- **Unit Test:** `<TargetClass>Test` hoặc `<Domain>ServiceImplTest`.
- **Integration Test:** `<Domain><Feature>IntegrationTest` (VD: `BudgetCrudIntegrationTest`).
- **Repository Test:** `<Domain>RepositoryTest`.
