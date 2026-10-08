package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupTxCountHelperTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private GroupTransactionRepository transactionRepository;

    private GroupTxCountHelper helper;

    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        helper = new GroupTxCountHelper(stringRedisTemplate, transactionRepository);
    }

    @Test
    @DisplayName("getCount khi có trong cache (cache hit) thì không truy vấn CSDL")
    void getCount_cacheHit() {
        String key = "group:tx_count:" + groupId + ":non_pending";
        when(valueOperations.get(key)).thenReturn("42");

        long count = helper.getCount(groupId, true);

        assertEquals(42L, count);
        verify(transactionRepository, never()).countByGroupIdAndDeletedAtIsNullAndStatusNot(any(), any());
    }

    @Test
    @DisplayName("getCount khi không có trong cache (cache miss) thì đếm từ CSDL và lưu vào cache")
    void getCount_cacheMiss() {
        String key = "group:tx_count:" + groupId + ":non_pending";
        when(valueOperations.get(key)).thenReturn(null);
        when(transactionRepository.countByGroupIdAndDeletedAtIsNullAndStatusNot(groupId, GTransactionStatus.PENDING))
                .thenReturn(100L);

        long count = helper.getCount(groupId, true);

        assertEquals(100L, count);
        verify(valueOperations).set(eq(key), eq("100"), any(Duration.class));
    }

    @Test
    @DisplayName("increment khi giao dịch không phải pending thì tăng cả 2 key")
    void increment_confirmed() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        String allKey = "group:tx_count:" + groupId + ":all";

        when(stringRedisTemplate.hasKey(nonPendingKey)).thenReturn(true);
        when(stringRedisTemplate.hasKey(allKey)).thenReturn(true);

        helper.increment(groupId, false);

        verify(valueOperations).increment(nonPendingKey, 1L);
        verify(valueOperations).increment(allKey, 1L);
    }

    @Test
    @DisplayName("increment khi giao dịch pending thì chỉ tăng key all")
    void increment_pending() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        String allKey = "group:tx_count:" + groupId + ":all";

        when(stringRedisTemplate.hasKey(allKey)).thenReturn(true);

        helper.increment(groupId, true);

        verify(valueOperations, never()).increment(eq(nonPendingKey), anyLong());
        verify(valueOperations).increment(allKey, 1L);
    }

    @Test
    @DisplayName("decrement khi giao dịch không phải pending thì giảm cả 2 key")
    void decrement_confirmed() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        String allKey = "group:tx_count:" + groupId + ":all";

        when(stringRedisTemplate.hasKey(nonPendingKey)).thenReturn(true);
        when(stringRedisTemplate.hasKey(allKey)).thenReturn(true);

        helper.decrement(groupId, false);

        verify(valueOperations).decrement(nonPendingKey, 1L);
        verify(valueOperations).decrement(allKey, 1L);
    }

    @Test
    @DisplayName("onTransactionReviewed tăng biến đếm của key non_pending")
    void onTransactionReviewed_success() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        when(stringRedisTemplate.hasKey(nonPendingKey)).thenReturn(true);

        helper.onTransactionReviewed(groupId);

        verify(valueOperations).increment(nonPendingKey, 1L);
    }

    @Test
    @DisplayName("onTransactionsBulkReviewed tăng biến đếm số lượng lô cho key non_pending")
    void onTransactionsBulkReviewed_success() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        when(stringRedisTemplate.hasKey(nonPendingKey)).thenReturn(true);

        helper.onTransactionsBulkReviewed(groupId, 5);

        verify(valueOperations).increment(nonPendingKey, 5L);
    }

    @Test
    @DisplayName("evict xóa cả 2 key khỏi Redis")
    void evict_success() {
        String nonPendingKey = "group:tx_count:" + groupId + ":non_pending";
        String allKey = "group:tx_count:" + groupId + ":all";

        helper.evict(groupId);

        verify(stringRedisTemplate).delete(nonPendingKey);
        verify(stringRedisTemplate).delete(allKey);
    }
}
