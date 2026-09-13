# Finance AI Server - Workspace Rules

## 1. Project Overview

- I am a backend Java application.
- I am responsible for writing the server code.
- I use Java 17.
- I use Maven and Spring Boot.
- ưu tiên nguyên tắc SOLID hơn

## 2. Module Architecture Rules

> Quy tắc bắt buộc khi viết code trong dự án. Áp dụng cho mọi module (`user`, `order`, `payment`, ...).

### 2.1 Cấu trúc thư mục bắt buộc

Mỗi module PHẢI theo đúng cấu trúc sau, không thêm/bớt layer tuỳ tiện:

```text
module/
├── common/
│   ├── config/
│   ├── exception/ (BusinessException.java, GlobalExceptionHandler.java, ErrorCode.java)
│   ├── util/
│   └── constant/
└── <domain>/                  # user, order, payment, ...
    ├── controller/
    ├── service/
    │   ├── <Domain>Service.java       # interface — PUBLIC API
    │   └── impl/<Domain>ServiceImpl.java
    ├── repository/
    ├── entity/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/
    ├── helper/                # chỉ tạo khi thực sự có logic phức tạp
    └── exception/
```

**KHÔNG ĐƯỢC:**

- Đặt entity/repository/DTO ngoài module tương ứng.
- Tạo layer mới (VD: `manager/`, `facade/`) mà không thống nhất convention trước.

### 2.2 Cross-module dependency

**PHẢI:**

- Module A cần dữ liệu của module B → gọi qua `<B>Service` interface (public API), không import `Repository` hoặc
  `Entity` của B.
- Trả về DTO (`XxxSummaryResponse`), không trả `Entity` khi expose ra ngoài module.
  **KHÔNG ĐƯỢC:**
- Import `xxx.repository.*` hoặc `xxx.entity.*` từ module khác.
- Tạo circular dependency giữa 2 module.

### 2.3 Entity relationship xuyên module

**QUY TẮC ƯU TIÊN — dùng ID thuần, KHÔNG dùng `@ManyToOne` xuyên module:**

- Tránh coupling ở tầng JPA/DB giữa 2 module.
- Tránh `LazyInitializationException` khi entity được dùng ngoài transaction gốc.
- Ép mọi truy cập dữ liệu user phải đi qua Service.

### 2.4 Exception

**PHẢI:**

- Mọi exception nghiệp vụ PHẢI extend `BusinessException` (ở `common/exception/`).
- Mỗi domain định nghĩa exception riêng trong `<domain>/exception/`.
- Mỗi exception gắn với 1 `ErrorCode` có sẵn `HttpStatus`.
  **KHÔNG ĐƯỢC:**
- Throw `RuntimeException` trực tiếp trong service.
- Viết `@ExceptionHandler` riêng lẻ trong từng controller.

### 2.5 DTO

**PHẢI:**

- Tách `request/` và `response/`.
- Tách riêng theo mục đích: `CreateRequest`, `UpdateRequest`, `SearchRequest`, `SummaryResponse`, `DetailResponse`.
  **KHÔNG ĐƯỢC:**
- Trả `Entity` trực tiếp ở controller/response.
- Dùng 1 DTO chung cho create/update nếu 2 field set khác nhau.

### 2.6 Service / Helper / Validator / Util

- **private method**: trong service cho logic ngắn.
- **`<domain>/helper/`**: logic nghiệp vụ phức tạp, tái sử dụng trong module.
- **`<domain>/validator/`**: validate phức tạp hơn annotation, ném exception.
- **`common/util/`**: pure function, không phụ thuộc domain.
  **Service PHẢI có interface + `impl/`**. KHÔNG ĐƯỢC tạo `helper/` thừa cho CRUD đơn giản.

### 2.7 Naming convention

- Entity: danh từ số ít (`User`, `Order`).
- Request: `<Domain><Action>Request` (`UserCreateRequest`).
- Response: `<Domain>Response`, `<Domain>DetailResponse`, `<Domain>SummaryResponse`.
- Exception: `<Domain><LỗiGì>Exception` (`UserNotFoundException`).
- Service interface: ưu tiên tên theo hành vi nghiệp vụ (`registerUser()`).
