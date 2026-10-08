package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLevelCacheHelperTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserLevelCacheHelper cacheHelper;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cacheHelper = new UserLevelCacheHelper(userRepository, stringRedisTemplate);
    }

    @Test
    @DisplayName("getUserLevel: Cache-hit lấy từ Redis mà không gọi CSDL")
    void getUserLevel_CacheHit_ReturnsFromRedisWithoutCallingDb() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:level:" + userId)).thenReturn("1");

        int level = cacheHelper.getUserLevel(userId);

        assertThat(level).isEqualTo(1);
        verify(userRepository, never()).findTopRoleLevelByUserId(any());
    }

    @Test
    @DisplayName("getUserLevel: Cache-miss gọi CSDL và lưu kết quả vào Redis")
    void getUserLevel_CacheMiss_CallsDbAndSetsRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:level:" + userId)).thenReturn(null);
        when(userRepository.findTopRoleLevelByUserId(userId)).thenReturn(10);

        int level = cacheHelper.getUserLevel(userId);

        assertThat(level).isEqualTo(10);
        verify(userRepository).findTopRoleLevelByUserId(userId);
        verify(valueOperations).set(eq("user:level:" + userId), eq("10"), any(Duration.class));
    }

    @Test
    @DisplayName("getUserLevel: Redis lỗi tự động fallback truy vấn CSDL bình thường")
    void getUserLevel_RedisError_FallsBackToDb() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        when(userRepository.findTopRoleLevelByUserId(userId)).thenReturn(5);

        int level = cacheHelper.getUserLevel(userId);

        assertThat(level).isEqualTo(5);
        verify(userRepository).findTopRoleLevelByUserId(userId);
    }

    @Test
    @DisplayName("getUserLevel: userId null trả về Integer.MAX_VALUE không chạm DB")
    void getUserLevel_NullUserId_ReturnsMaxInt() {
        int level = cacheHelper.getUserLevel(null);

        assertThat(level).isEqualTo(Integer.MAX_VALUE);
        verifyNoInteractions(userRepository, stringRedisTemplate);
    }

    @Test
    @DisplayName("evictUserLevel: Xóa đúng key Redis của user")
    void evictUserLevel_DeletesRedisKey() {
        cacheHelper.evictUserLevel(userId);

        verify(stringRedisTemplate).delete("user:level:" + userId);
    }
}
