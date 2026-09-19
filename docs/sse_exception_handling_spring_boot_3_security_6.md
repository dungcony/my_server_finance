# Xử lý Exception đẹp cho SSE trong Spring Boot 3 + Spring Security 6

## 1. Bối cảnh

Endpoint:

``` text
GET /v1/admin/logs/stream
```

có khả năng là một endpoint **Server-Sent Events (SSE)**.

SSE giữ một HTTP connection mở để server liên tục gửi dữ liệu về client.
Vì vậy, khi người dùng:

-   F5 trang
-   đóng tab
-   chuyển trang
-   mất mạng
-   frontend chủ động đóng `EventSource`

thì connection SSE cũ sẽ bị đóng.

Việc server nhận exception khi client disconnect **có thể là hành vi
bình thường**. Điều cần làm là phân biệt trường hợp này với lỗi thực sự
của application.

------------------------------------------------------------------------

# 2. Vấn đề trong log hiện tại

Ví dụ log:

``` text
GET /v1/admin/logs/stream → 200 → 467ms
GET /v1/admin/logs/stream → 200 → 9ms

ERROR Lỗi ngoài dự kiến tại GET /v1/admin/logs/stream
WARN  Failure in @ExceptionHandler
ERROR Servlet.service() for servlet [dispatcherServlet] threw exception
```

Điểm đáng chú ý nhất là:

``` text
Failure in @ExceptionHandler
GlobalExceptionHandler#handleUnexpected(...)
```

Điều này cho thấy exception đã đi vào handler tổng quát, trong khi
nguyên nhân ban đầu có thể chỉ là client đã đóng SSE connection.

Tuy nhiên, **không nên kết luận chỉ từ log này** rằng nguyên nhân chắc
chắn là client disconnect hoặc `AuthorizationDeniedException`. Cần xem
stack trace/root cause thực tế.

------------------------------------------------------------------------

# 3. Với Spring Boot 3 + Spring Security 6: có thêm Security layer

Đây là phần quan trọng khi ứng dụng sử dụng `SseEmitter` hoặc các cơ chế
Servlet async.

Request ban đầu có dispatcher type:

``` text
DispatcherType.REQUEST
```

Sau khi request chuyển sang async processing, Spring MVC/Servlet
container có thể thực hiện các async/error dispatch tùy lifecycle.

Spring Security 6 có thể áp dụng authorization cho các dispatcher type
này.

Do đó, với ứng dụng có async/SSE, nên cấu hình rõ:

``` java
.dispatcherTypeMatchers(
    DispatcherType.ASYNC,
    DispatcherType.ERROR
).permitAll()
```

Ví dụ:

``` java
http
    .authorizeHttpRequests(auth -> auth
        .dispatcherTypeMatchers(
            DispatcherType.ASYNC,
            DispatcherType.ERROR
        ).permitAll()

        .requestMatchers("/api/auth/**").permitAll()

        .anyRequest().authenticated()
    );
```

## Vì sao cần cấu hình này?

Nếu async/error dispatch bị áp dụng rule:

``` java
.anyRequest().authenticated()
```

thì trong một số lifecycle của async request, Security có thể từ chối
dispatch trước khi request đi tới Controller hoặc
`GlobalExceptionHandler`.

Một exception có thể xuất hiện dạng:

``` text
AuthorizationDeniedException: Access Denied
```

hoặc lỗi liên quan tới authorization.

### Nhưng cần lưu ý

Không nên hiểu rằng:

``` text
F5
 ↓
ASYNC dispatch
 ↓
AuthorizationFilter
 ↓
AuthorizationDeniedException
```

là flow **bắt buộc**.

Flow thực tế phụ thuộc vào:

-   cách triển khai SSE
-   `SseEmitter` hay WebFlux
-   Servlet container
-   lifecycle của `AsyncContext`
-   Security filter chain
-   dispatcher type
-   request matcher
-   thời điểm client disconnect

Vì vậy, `ASYNC/ERROR permitAll()` là một cấu hình phòng tránh phù hợp
cho async/SSE, nhưng **không thể dùng nó để kết luận nguyên nhân của mọi
lỗi F5**.

------------------------------------------------------------------------

# 4. Nên xử lý SSE ở hai tầng

Với Spring Boot 3 + Spring Security 6, nên tư duy theo hai tầng:

``` text
                 GET /v1/admin/logs/stream
                              │
                              ▼
                     Spring Security
                              │
                  ┌───────────┴───────────┐
                  │                       │
              REQUEST                ASYNC / ERROR
                  │                       │
                  ▼                       ▼
             authorize                 permitAll
                  │                       │
                  └───────────┬───────────┘
                              ▼
                       Spring MVC / SSE
                              │
                              ▼
                     Client disconnect
                              │
                              ▼
                    SseEmitter lifecycle
                              │
                   ┌──────────┴──────────┐
                   │                     │
                cleanup            expected exception
                                         │
                                         ▼
                                  DEBUG / ignore
```

Nếu exception thực sự là lỗi application:

``` text
Unexpected application exception
            ↓
GlobalExceptionHandler
            ↓
ERROR + stack trace
            ↓
HTTP 500
```

------------------------------------------------------------------------

# 5. Không để client disconnect rơi vào `handleUnexpected()`

Ví dụ handler tổng quát:

``` java
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handleUnexpected(
        Exception ex,
        HttpServletRequest request
) {
    log.error(
        "Unexpected error at {}",
        request.getRequestURI(),
        ex
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(...);
}
```

Handler này nên dành cho lỗi thực sự không dự kiến.

Nếu exception do client đóng SSE connection đi vào đây:

``` text
F5
 ↓
SSE connection đóng
 ↓
Client disconnect exception
 ↓
handleUnexpected()
 ↓
ERROR
```

thì log sẽ bị "đỏ" dù không phải application failure.

------------------------------------------------------------------------

# 6. Handle riêng các exception do disconnect

Một số stack có thể phát sinh:

``` text
ClientAbortException
Broken pipe
AsyncRequestNotUsableException
```

Exception cụ thể phụ thuộc vào Spring Boot, Tomcat và cách triển khai.

Ví dụ với Tomcat:

``` java
import org.apache.catalina.connector.ClientAbortException;

@ExceptionHandler(ClientAbortException.class)
public void handleClientAbort(
        ClientAbortException ex,
        HttpServletRequest request
) {
    log.debug(
        "Client đã đóng SSE connection: {}",
        request.getRequestURI()
    );
}
```

Không nên copy cứng exception này nếu stack trace thực tế của project
không sử dụng nó.

Hãy kiểm tra **root cause** trước rồi handle đúng exception type.

------------------------------------------------------------------------

# 7. Xử lý lifecycle của `SseEmitter`

Nếu endpoint sử dụng `SseEmitter`, nên đăng ký lifecycle callback:

``` java
@GetMapping(
    value = "/logs/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE
)
public SseEmitter streamLogs() {

    SseEmitter emitter = new SseEmitter(0L);

    emitter.onCompletion(() ->
        log.debug("SSE connection completed")
    );

    emitter.onTimeout(() -> {
        log.debug("SSE connection timeout");
        emitter.complete();
    });

    emitter.onError(error ->
        log.debug(
            "SSE connection error/closed: {}",
            error.getMessage()
        )
    );

    return emitter;
}
```

Mục tiêu:

``` text
Client F5
    ↓
Connection cũ đóng
    ↓
SSE lifecycle callback
    ↓
Cleanup
    ↓
DEBUG / ignore nếu đây là expected disconnect
```

------------------------------------------------------------------------

# 8. Cleanup subscriber/listener

Nếu `/logs/stream` đăng ký listener để nhận log realtime, cần đặc biệt
chú ý cleanup.

Ví dụ:

``` java
SseEmitter emitter = new SseEmitter(0L);

logService.subscribe(emitter);

emitter.onCompletion(() ->
    logService.unsubscribe(emitter)
);

emitter.onTimeout(() -> {
    logService.unsubscribe(emitter);
    emitter.complete();
});

emitter.onError(error ->
    logService.unsubscribe(emitter)
);
```

Nếu không cleanup, F5 nhiều lần có thể tạo:

``` text
Browser
   │
   ├── SSE #1
   ├── SSE #2
   ├── SSE #3
   ├── SSE #4
   └── SSE #5
```

trong khi server vẫn giữ reference tới các emitter cũ.

Điều này có thể dẫn tới:

-   memory leak
-   listener bị tích tụ
-   gửi event vào connection đã chết
-   CPU/network overhead
-   log lỗi liên tục

------------------------------------------------------------------------

# 9. `GlobalExceptionHandler` vẫn phải giữ

Không nên xóa:

``` java
@ExceptionHandler(Exception.class)
```

Handler tổng quát vẫn rất cần thiết.

Ví dụ:

``` java
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handleUnexpected(
        Exception ex,
        HttpServletRequest request
) {
    log.error(
        "Unexpected error at {}",
        request.getRequestURI(),
        ex
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(...);
}
```

Mục tiêu là để các exception đã biết được xử lý trước:

``` text
Client disconnect
       ↓
Specific handler / SSE lifecycle
       ↓
DEBUG / cleanup

BusinessException
       ↓
Specific handler
       ↓
4xx

ValidationException
       ↓
Specific handler
       ↓
400

Unexpected Exception
       ↓
handleUnexpected()
       ↓
ERROR / 500
```

------------------------------------------------------------------------

# 10. Phân loại log level

Một hệ thống log hợp lý có thể phân loại:

  Tình huống                  Log level
  --------------------------- ---------------------
  Login thành công            `INFO`
  SSE connection được tạo     `DEBUG` hoặc `INFO`
  SSE client disconnect       `DEBUG`
  SSE completed               `DEBUG`
  Expected timeout            `DEBUG` hoặc `WARN`
  Validation/business error   `WARN`
  Database/system failure     `ERROR`
  Unexpected exception        `ERROR`

Không nên biến:

``` text
F5
 ↓
client disconnect
 ↓
ERROR 🔴
```

thành lỗi server.

------------------------------------------------------------------------

# 11. Cách kiểm tra chính xác lỗi hiện tại

Không nên chỉ nhìn:

``` text
Failure in @ExceptionHandler
```

Hãy tìm stack trace đầy đủ và xác định exception gốc.

### Trường hợp A --- Client disconnect

Ví dụ:

``` text
ClientAbortException
Broken pipe
AsyncRequestNotUsableException
```

→ Có thể là expected khi F5/đóng tab.

Xử lý:

``` text
cleanup
+
DEBUG / ignore
```

------------------------------------------------------------------------

### Trường hợp B --- Security authorization

Nếu thấy:

``` text
AuthorizationDeniedException: Access Denied
```

→ Kiểm tra:

``` java
.dispatcherTypeMatchers(
    DispatcherType.ASYNC,
    DispatcherType.ERROR
).permitAll()
```

và toàn bộ `SecurityFilterChain`.

Đặc biệt kiểm tra rule:

``` java
.anyRequest().authenticated()
```

có đang bắt async/error dispatch hay không.

------------------------------------------------------------------------

### Trường hợp C --- Application exception

Ví dụ:

``` text
NullPointerException
DataAccessException
IllegalStateException
...
```

→ Đây không phải lỗi F5 bình thường.

Cần sửa nguyên nhân trong application.

------------------------------------------------------------------------

# 12. `PathRequest.toStaticResources()` là chuyện khác

Config:

``` java
.requestMatchers(
    PathRequest.toStaticResources().atCommonLocations()
).permitAll()
```

chủ yếu dùng cho static resources ở các location phổ biến.

Nó không phải cơ chế xử lý SSE disconnect.

Nếu backend của bạn serve frontend/static resources từ Spring Boot thì
nên giữ/điều chỉnh theo cấu trúc ứng dụng.

Nếu frontend được deploy riêng và backend chỉ là REST API thì matcher
này thường không đóng vai trò trong `/v1/admin/logs/stream`.

------------------------------------------------------------------------

# 13. Hai matcher `ASYNC/ERROR` có ảnh hưởng performance không?

Ví dụ:

``` java
.dispatcherTypeMatchers(
    DispatcherType.ASYNC,
    DispatcherType.ERROR
).permitAll()
```

Không nên xem đây là cách tối ưu latency.

Nó phục vụ **đúng authorization behavior của async/error dispatch**.

Đối với API login:

``` text
Request
 ↓
DB query
 ↓
PasswordEncoder.matches()
 ↓
JWT
 ↓
Response
```

những phần trên thường quan trọng hơn nhiều đối với latency.

Vì vậy:

> Giữ `ASYNC/ERROR permitAll()` nếu ứng dụng có async/SSE không phải để
> login nhanh hơn, mà để security không vô tình cản trở lifecycle của
> async request.

------------------------------------------------------------------------

# 14. Flow "đẹp" cuối cùng

Kiến trúc nên hướng tới:

``` text
                        SSE REQUEST
                            │
                            ▼
                    Spring Security
                            │
              ┌─────────────┴─────────────┐
              │                           │
           REQUEST                    ASYNC / ERROR
              │                           │
              ▼                           ▼
        normal auth                 permitAll
              │                           │
              └─────────────┬─────────────┘
                            ▼
                       SSE Controller
                            │
                            ▼
                       SseEmitter
                            │
               ┌────────────┼────────────┐
               │            │            │
          normal data    timeout     disconnect
               │            │            │
               ▼            ▼            ▼
             200         cleanup      cleanup
                                         │
                                         ▼
                                  DEBUG / ignore
```

Còn application failure:

``` text
                    Application failure
                            │
                            ▼
                  Specific handler?
                       │       │
                      YES      NO
                       │       │
                       ▼       ▼
                     4xx    handleUnexpected
                                  │
                                  ▼
                              ERROR + 500
```

------------------------------------------------------------------------

# 15. Checklist cho project

## Security

-   [ ] Có `DispatcherType.ASYNC` cho async/SSE.
-   [ ] Có `DispatcherType.ERROR` nếu muốn error dispatch không bị
    authorization rule chặn.
-   [ ] Kiểm tra thứ tự matcher trước `.anyRequest().authenticated()`.
-   [ ] Không dùng `permitAll()` rộng hơn phạm vi cần thiết.

## SSE

-   [ ] Có `onCompletion()`.
-   [ ] Có `onTimeout()`.
-   [ ] Có `onError()`.
-   [ ] Cleanup subscriber/listener khi connection chết.
-   [ ] Không gửi event vô hạn vào emitter đã chết.

## Exception

-   [ ] Client disconnect được phân biệt với server failure.
-   [ ] Exception expected không bị log `ERROR`.
-   [ ] `GlobalExceptionHandler` vẫn giữ cho lỗi unexpected.
-   [ ] Kiểm tra root cause trước khi thêm `@ExceptionHandler`.

## Logging

-   [ ] Expected disconnect → `DEBUG`.
-   [ ] Business/validation issue → `WARN` hoặc response 4xx phù hợp.
-   [ ] Unexpected server failure → `ERROR`.
-   [ ] Không dùng latency của SSE để đánh giá latency REST API.

------------------------------------------------------------------------

# 16. Kết luận

Đối với Spring Boot 3 + Spring Security 6 sử dụng SSE, nên xử lý theo
**hai tầng**:

### Tầng 1 --- Spring Security

Cho phép async/error dispatch phù hợp:

``` java
.dispatcherTypeMatchers(
    DispatcherType.ASYNC,
    DispatcherType.ERROR
).permitAll()
```

Điều này giúp tránh authorization rules thông thường can thiệp vào
async/error dispatch trong những lifecycle phù hợp.

### Tầng 2 --- SSE + Exception handling

Xử lý lifecycle:

``` text
onCompletion()
onTimeout()
onError()
```

và phân biệt:

``` text
Expected client disconnect
        ↓
cleanup
        ↓
DEBUG / ignore
```

với:

``` text
Unexpected application exception
        ↓
GlobalExceptionHandler
        ↓
ERROR + 500
```

Điểm quan trọng nhất:

> **Không nên mặc định coi mọi exception xuất hiện khi F5 là lỗi server,
> nhưng cũng không nên mặc định rằng mọi lỗi F5 đều là
> `ClientAbortException` hoặc `AuthorizationDeniedException`. Hãy xác
> định root cause từ stack trace rồi xử lý đúng tầng.**

Đây là cách vừa sạch log, vừa đúng với lifecycle của SSE, vừa tránh để
Spring Security và GlobalExceptionHandler vô tình biến một client
disconnect bình thường thành một lỗi application.
