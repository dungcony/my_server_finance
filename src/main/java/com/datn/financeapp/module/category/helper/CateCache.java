package com.datn.financeapp.module.category.helper;

import com.datn.financeapp.module.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache RAM hai chiều id ↔ {@code code} của bảng {@code categories}, dùng để tra ngược nhanh
 * và kiểm tra {@code code} đã tồn tại mà không query DB.
 * <p>
 * <b>Quy ước nghiệp vụ:</b> {@code code} duy nhất toàn hệ thống. Danh mục xóa mềm vẫn giữ chỗ
 * {@code code} trong cache (nạp cả bản ghi {@code deleted = true}), nên không tạo mới trùng được
 * và vẫn tra ngược được id cũ của giao dịch đã phát sinh.
 * </p>
 * <p>
 * <b>Tác dụng phụ (Side-effects):</b>
 * <ul>
 *   <li>Nạp toàn bộ một lần khi ứng dụng sẵn sàng ({@link ApplicationReadyEvent}).</li>
 *   <li>Cache KHÔNG tự cập nhật theo DB: bên gọi phải gọi {@link #register} / {@link #changeCode}
 *       sau khi transaction commit, nếu gọi trước mà transaction rollback thì cache bị lệch.</li>
 *   <li>Xóa mềm không cần gọi gì. Chỉ đúng khi chạy một instance; nhiều instance cần cơ chế
 *       đồng bộ khác.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Các hàm:</b> {@link #loadAll} nạp lại toàn bộ từ DB; {@link #findCode} tra code theo id;
 * {@link #findId} tra id theo code; {@link #existsCode} kiểm code đã có người giữ chưa;
 * {@link #register} ghi code của danh mục mới; {@link #changeCode} đổi code danh mục đã có.
 * Tham số {@code null} hoặc code rỗng cho kết quả "không có"/{@code false}, không ném lỗi.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CateCache {

    private final CategoryRepository categoryRepository;
    private volatile Map<UUID, String> idToCode = new ConcurrentHashMap<>();
    private volatile Map<String, UUID> codeToId = new ConcurrentHashMap<>();

    /**
     * Nạp lại toàn bộ cặp (id, code), kể cả danh mục đã xóa mềm.
     * <p>
     * Dựng cặp map mới rồi hoán đổi một lần, nên truy vấn đọc đang chạy không thấy cache rỗng
     * hay thiếu giữa chừng. Hai map được hoán đổi lần lượt nên có thể lệch nhau trong chốc lát.
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void loadAll() {
        List<CategoryCodeView> rows = categoryRepository.findAllCodes();
        Map<UUID, String> newIdToCode = new ConcurrentHashMap<>();
        Map<String, UUID> newCodeToId = new ConcurrentHashMap<>();
        rows.forEach(row -> {
            newIdToCode.put(row.id(), row.code());
            newCodeToId.put(row.code(), row.id());
        });
        idToCode = newIdToCode;
        codeToId = newCodeToId;
        log.info("Đã nạp {} code danh mục vào cache", rows.size());
    }

    /**
     * Tra code theo id.
     *
     * @param id ID danh mục
     * @return code của danh mục, rỗng nếu id chưa có trong cache
     */
    public Optional<String> findCode(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(idToCode.get(id));
    }

    /**
     * Tra id theo code.
     *
     * @param code code danh mục
     * @return id của danh mục, rỗng nếu code chưa tồn tại
     */
    public Optional<UUID> findId(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(codeToId.get(code));
    }

    /**
     * Kiểm tra code đã được danh mục nào giữ chưa (kể cả danh mục đã xóa mềm).
     *
     * @param code code cần kiểm tra
     * @return {@code true} nếu code đã tồn tại
     */
    public boolean existsCode(String code) {
        return code != null && codeToId.containsKey(code);
    }

    /**
     * Ghi nhận code của danh mục mới tạo.
     *
     * @param id   ID danh mục mới
     * @param code code muốn gán
     * @return {@code true} nếu gán được; {@code false} nếu code đã thuộc danh mục khác (cache không đổi)
     */
    public synchronized boolean register(UUID id, String code) {
        return assign(id, code);
    }

    /**
     * Đổi code của danh mục đã có, giải phóng code cũ.
     *
     * @param id      ID danh mục cần đổi
     * @param newCode code mới
     * @return {@code true} nếu đổi được; {@code false} nếu code mới đã thuộc danh mục khác (cache không đổi)
     */
    public synchronized boolean changeCode(UUID id, String newCode) {
        return assign(id, newCode);
    }

    private boolean assign(UUID id, String code) {
        if (id == null || code == null || code.isBlank()) {
            return false;
        }
        UUID owner = codeToId.get(code);
        if (owner != null) {
            return owner.equals(id);
        }
        String oldCode = idToCode.put(id, code);
        codeToId.put(code, id);
        if (oldCode != null) {
            codeToId.remove(oldCode, id);
        }
        return true;
    }
}
