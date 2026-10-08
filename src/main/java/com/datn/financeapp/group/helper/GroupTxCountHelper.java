package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Helper đóng gói quản lý bộ nhớ đệm (cache) số lượng giao dịch của nhóm.
 * <p>
 * Sử dụng Redis String với các thao tác nguyên tử (atomic INCR/DECR) để tránh
 * race condition khi nhiều luồng cùng tạo/xóa giao dịch đồng thời.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #getCount}: Lấy tổng số giao dịch từ cache, nạp từ CSDL nếu chưa có. Input: groupId, excludePending. Output: số lượng (long).</li>
 *   <li>{@link #increment}: Tăng biến đếm nguyên tử khi tạo mới giao dịch. Input: groupId, isPending.</li>
 *   <li>{@link #decrement}: Giảm biến đếm nguyên tử khi xóa giao dịch. Input: groupId, wasPending.</li>
 *   <li>{@link #onTransactionReviewed}: Tăng biến đếm danh sách không pending khi duyệt giao dịch. Input: groupId.</li>
 *   <li>{@link #onTransactionsBulkReviewed}: Tăng biến đếm danh sách không pending khi duyệt hàng loạt. Input: groupId, count.</li>
 *   <li>{@link #evict}: Xóa key cache khi cần đồng bộ lại dữ liệu. Input: groupId.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupTxCountHelper {

    private static final String KEY_PREFIX = "group:tx_count:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final GroupTransactionRepository transactionRepository;

    /**
     * Lấy tổng số lượng giao dịch của nhóm (có cache).
     *
     * @param groupId        ID nhóm
     * @param excludePending cờ xác định có loại trừ trạng thái PENDING không
     * @return số lượng giao dịch
     */
    public long getCount(UUID groupId, boolean excludePending) {
        String key = buildKey(groupId, excludePending);
        try {
            String val = stringRedisTemplate.opsForValue().get(key);
            if (val != null) {
                return Long.parseLong(val);
            }
        } catch (Exception e) {
            log.warn("Không thể đọc cache số lượng giao dịch nhóm {}: {}", groupId, e.getMessage());
        }

        // cache miss: đếm từ CSDL và lưu lại vào Redis
        long count = countFromDb(groupId, excludePending);
        setCache(key, count);
        return count;
    }

    /**
     * Tăng biến đếm khi tạo mới giao dịch.
     *
     * @param groupId   ID nhóm
     * @param isPending giao dịch vừa tạo có ở trạng thái PENDING hay không
     */
    public void increment(UUID groupId, boolean isPending) {
        // nếu không phải pending, tăng biến đếm của danh sách chính
        if (!isPending) {
            incrementKey(buildKey(groupId, true), 1);
        }
        // luôn tăng biến đếm của toàn bộ giao dịch nhóm
        incrementKey(buildKey(groupId, false), 1);
    }

    /**
     * Giảm biến đếm khi xóa giao dịch.
     *
     * @param groupId    ID nhóm
     * @param wasPending giao dịch bị xóa có ở trạng thái PENDING hay không
     */
    public void decrement(UUID groupId, boolean wasPending) {
        // nếu giao dịch không phải pending, giảm biến đếm của danh sách chính
        if (!wasPending) {
            decrementKey(buildKey(groupId, true), 1);
        }
        // luôn giảm biến đếm của toàn bộ giao dịch nhóm
        decrementKey(buildKey(groupId, false), 1);
    }

    /**
     * Cập nhật biến đếm khi một giao dịch PENDING được duyệt (CONFIRMED hoặc REJECTED).
     *
     * @param groupId ID nhóm
     */
    public void onTransactionReviewed(UUID groupId) {
        incrementKey(buildKey(groupId, true), 1);
    }

    /**
     * Cập nhật biến đếm khi duyệt hàng loạt giao dịch PENDING.
     *
     * @param groupId ID nhóm
     * @param count   số lượng giao dịch vừa duyệt
     */
    public void onTransactionsBulkReviewed(UUID groupId, int count) {
        if (count > 0) {
            incrementKey(buildKey(groupId, true), count);
        }
    }

    /**
     * Xóa cache số lượng giao dịch của nhóm.
     *
     * @param groupId ID nhóm
     */
    public void evict(UUID groupId) {
        try {
            stringRedisTemplate.delete(buildKey(groupId, true));
            stringRedisTemplate.delete(buildKey(groupId, false));
        } catch (Exception e) {
            log.warn("Lỗi xóa cache số lượng giao dịch nhóm {}: {}", groupId, e.getMessage());
        }
    }

    private void incrementKey(String key, long delta) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForValue().increment(key, delta);
            }
        } catch (Exception e) {
            log.warn("Lỗi tăng biến đếm cache key {}: {}", key, e.getMessage());
        }
    }

    private void decrementKey(String key, long delta) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForValue().decrement(key, delta);
            }
        } catch (Exception e) {
            log.warn("Lỗi giảm biến đếm cache key {}: {}", key, e.getMessage());
        }
    }

    private void setCache(String key, long count) {
        try {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(count), TTL);
        } catch (Exception e) {
            log.warn("Lỗi ghi cache số lượng giao dịch: {}", e.getMessage());
        }
    }

    private long countFromDb(UUID groupId, boolean excludePending) {
        if (excludePending) {
            return transactionRepository.countByGroupIdAndDeletedAtIsNullAndStatusNot(groupId, GTransactionStatus.PENDING);
        }
        return transactionRepository.countByGroupIdAndDeletedAtIsNull(groupId);
    }

    private String buildKey(UUID groupId, boolean excludePending) {
        return KEY_PREFIX + groupId + (excludePending ? ":non_pending" : ":all");
    }
}
