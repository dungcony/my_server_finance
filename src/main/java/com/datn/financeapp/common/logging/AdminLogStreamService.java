package com.datn.financeapp.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AdminLogStreamService {

    private static final int MAX_BUFFER_SIZE = 1000;
    // Hàng đợi giữa thread ghi log (bất kỳ thread nào của app) và thread gửi SSE riêng.
    // Mục đích: một client SSE mạng chậm không được phép làm nghẽn thread đang xử lý
    // request/nghiệp vụ thật của app chỉ vì nó gọi log.info(...).
    private static final int SEND_QUEUE_CAPACITY = 2000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    private static final java.util.regex.Pattern HTTP_ACCESS_PATTERN =
            java.util.regex.Pattern.compile("^<--\\s+([A-Z]+)\\s+([^\\s|]+)\\s+\\|\\s+status=(\\d+)\\s+\\|\\s+time=(\\d+)ms");

    private final AtomicLong seq = new AtomicLong(0);
    private final Deque<AdminLogEvent> buffer = new ArrayDeque<>(MAX_BUFFER_SIZE);
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final BlockingQueue<AdminLogEvent> sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "admin-log-sse-sender");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;
    private RealtimeLogAppender appender;

    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new RealtimeLogAppender();
        appender.setContext(context);
        appender.setName("ADMIN_REALTIME_LOG_APPENDER");
        appender.start();

        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);

        senderExecutor.submit(this::drainQueueLoop);
    }

    @PreDestroy
    public void cleanup() {
        running = false;
        senderExecutor.shutdownNow();

        if (appender != null) {
            appender.stop();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        emitters.clear();
    }

    public SseEmitter createEmitter() {
        // Timeout 30 phút, client có thể reconnect
        SseEmitter emitter = new SseEmitter(1800000L);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE log stream đã hoàn tất (onCompletion)");
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE log stream hết hạn timeout, đóng emitter (onTimeout)");
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE log stream ngắt kết nối (onError): {}", e != null ? e.getMessage() : "Client disconnect");
        });

        // Gửi thông báo kết nối thành công + toàn bộ buffer log gần nhất
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Kết nối Realtime Log thành công!"));

            List<AdminLogEvent> history = getHistory();
            for (AdminLogEvent event : history) {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .id(String.valueOf(event.getId()))
                        .data(event));
            }
        } catch (Exception e) {
            emitter.complete();
            return emitter;
        }

        emitters.add(emitter);
        return emitter;
    }

    private synchronized void addToBuffer(AdminLogEvent event) {
        if (buffer.size() >= MAX_BUFFER_SIZE) {
            buffer.pollFirst();
        }
        buffer.addLast(event);
    }

    public synchronized List<AdminLogEvent> getHistory() {
        return new ArrayList<>(buffer);
    }

    /**
     * Thread riêng, chuyên lấy event từ {@link #sendQueue} và gửi qua SSE tới mọi client
     * đang kết nối. Tách khỏi thread ghi log gốc để một client chậm không làm chậm cả app.
     */
    private void drainQueueLoop() {
        while (running) {
            AdminLogEvent event;
            try {
                event = sendQueue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (event == null || emitters.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("log")
                            .id(String.valueOf(event.getId()))
                            .data(event));
                } catch (Exception e) {
                    emitters.remove(emitter);
                    log.debug("Gỡ bỏ emitter do lỗi gửi event (client ngắt kết nối): {}", e.getMessage());
                }
            }
        }
    }

    private static String classifySource(String loggerName) {
        if (loggerName == null) {
            return "App";
        }
        if (loggerName.contains("RequestLoggingFilter")) {
            return "HTTP";
        }
        if (loggerName.contains(".common.security") || loggerName.contains("JwtAuth")) {
            return "Security";
        }
        if (loggerName.contains(".auth.") || loggerName.startsWith("com.datn.financeapp.auth")) {
            return "Auth";
        }
        if (loggerName.startsWith("org.hibernate") || loggerName.contains(".SQL")) {
            return "Database";
        }
        return "App";
    }

    private static String extractParam(String msg, String key) {
        int idx = msg.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        int end = msg.length();
        for (int i = start; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c == ' ' || c == '|' || c == ']' || c == ',') {
                end = i;
                break;
            }
        }
        String val = msg.substring(start, end).trim();
        return val.isEmpty() ? null : val;
    }

    private class RealtimeLogAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent eventObject) {
            // Chặn đệ quy do chính log streamer sinh ra
            String loggerName = eventObject.getLoggerName();
            if (loggerName != null && loggerName.contains("AdminLog")) {
                return;
            }

            Map<String, String> mdc = eventObject.getMDCPropertyMap();
            String requestId = mdc != null ? mdc.get("requestId") : null;
            String userId = mdc != null ? mdc.get("userId") : null;
            String api = mdc != null ? mdc.get("api") : null;
            Integer status = null;
            Long durationMs = null;

            String rawMessage = eventObject.getFormattedMessage();
            if (rawMessage != null && rawMessage.startsWith("<-- ")) {
                java.util.regex.Matcher matcher = HTTP_ACCESS_PATTERN.matcher(rawMessage);
                if (matcher.find()) {
                    String path = matcher.group(2);
                    int qIdx = path.indexOf('?');
                    if (qIdx != -1) {
                        path = path.substring(0, qIdx);
                    }
                    api = matcher.group(1) + " " + path;
                    status = Integer.parseInt(matcher.group(3));
                    durationMs = Long.parseLong(matcher.group(4));
                }
            }

            if (rawMessage != null) {
                if (requestId == null) {
                    requestId = extractParam(rawMessage, "reqId=");
                }
                if (userId == null) {
                    userId = extractParam(rawMessage, "user=");
                }
            }

            IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
            String stackTrace = throwableProxy != null
                    ? LogMasker.mask(ThrowableProxyUtil.asString(throwableProxy))
                    : null;

            AdminLogEvent event = AdminLogEvent.builder()
                    .id(seq.incrementAndGet())
                    .timestamp(TIME_FORMATTER.format(Instant.ofEpochMilli(eventObject.getTimeStamp())))
                    .level(eventObject.getLevel().toString())
                    .logger(loggerName)
                    .thread(eventObject.getThreadName())
                    .message(LogMasker.mask(rawMessage))
                    .requestId(requestId)
                    .userId(userId)
                    .source(classifySource(loggerName))
                    .stackTrace(stackTrace)
                    .api(api)
                    .status(status)
                    .durationMs(durationMs)
                    .build();

            addToBuffer(event);

            // offer() không chặn: nếu hàng đợi đầy (client SSE quá chậm) thì chấp nhận
            // rớt dòng log mới nhất thay vì chặn thread đang ghi log (có thể là thread
            // đang xử lý request nghiệp vụ thật).
            sendQueue.offer(event);
        }
    }
}
