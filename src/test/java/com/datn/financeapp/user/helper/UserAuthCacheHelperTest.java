package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthCacheHelperTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private UserAuthCacheHelper cacheHelper;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cacheHelper = new UserAuthCacheHelper(userRepository, stringRedisTemplate);
    }

    @Test
    @DisplayName("getUserLevel: Cache-hit lấy từ Redis Hash mà không gọi CSDL")
    void getUserLevel_CacheHit_ReturnsFromRedisWithoutCallingDb() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("user:auth:" + userId, "level")).thenReturn("1");

        int level = cacheHelper.getUserLevel(userId);

        assertThat(level).isEqualTo(1);
        verify(userRepository, never()).findTopRoleLevelByUserId(any());
    }

    @Test
    @DisplayName("getUserLevel: Cache-miss gọi CSDL và lưu kết quả vào Redis Hash")
    void getUserLevel_CacheMiss_CallsDbAndSetsRedis() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("user:auth:" + userId, "level")).thenReturn(null);
        when(userRepository.findTopRoleLevelByUserId(userId)).thenReturn(10);

        int level = cacheHelper.getUserLevel(userId);

        assertThat(level).isEqualTo(10);
        verify(userRepository).findTopRoleLevelByUserId(userId);
        verify(hashOperations).put("user:auth:" + userId, "level", "10");
        verify(stringRedisTemplate).expire(eq("user:auth:" + userId), any(Duration.class));
    }

    @Test
    @DisplayName("getUserLevel: Redis lỗi tự động fallback truy vấn CSDL bình thường")
    void getUserLevel_RedisError_FallsBackToDb() {
        when(stringRedisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis connection refused"));
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
    @DisplayName("getUserAuthorities: Cache-hit lấy từ Redis Hash mà không gọi CSDL")
    void getUserAuthorities_CacheHit_ReturnsFromRedisWithoutCallingDb() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("user:auth:" + userId, "authorities")).thenReturn("users:read,ROLE_ADMIN");

        List<String> authorities = cacheHelper.getUserAuthorities(userId);

        assertThat(authorities).containsExactly("users:read", "ROLE_ADMIN");
        verify(userRepository, never()).findAuthoritiesByUserId(any());
    }

    @Test
    @DisplayName("getUserAuthorities: Cache-miss gọi CSDL và lưu kết quả vào Redis Hash")
    void getUserAuthorities_CacheMiss_CallsDbAndSetsRedis() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("user:auth:" + userId, "authorities")).thenReturn(null);
        when(userRepository.findAuthoritiesByUserId(userId)).thenReturn(List.of("users:read", "ROLE_USER"));

        List<String> authorities = cacheHelper.getUserAuthorities(userId);

        assertThat(authorities).containsExactly("users:read", "ROLE_USER");
        verify(userRepository).findAuthoritiesByUserId(userId);
        verify(hashOperations).put("user:auth:" + userId, "authorities", "users:read,ROLE_USER");
        verify(stringRedisTemplate).expire(eq("user:auth:" + userId), any(Duration.class));
    }

    @Test
    @DisplayName("getUserAuthorities: Redis lỗi tự động fallback truy vấn CSDL bình thường")
    void getUserAuthorities_RedisError_FallsBackToDb() {
        when(stringRedisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis connection refused"));
        when(userRepository.findAuthoritiesByUserId(userId)).thenReturn(List.of("users:read"));

        List<String> authorities = cacheHelper.getUserAuthorities(userId);

        assertThat(authorities).containsExactly("users:read");
        verify(userRepository).findAuthoritiesByUserId(userId);
    }

    @Test
    @DisplayName("evictUserAuth: Xóa đúng key Redis của user")
    void evictUserAuth_DeletesRedisKey() {
        cacheHelper.evictUserAuth(userId);

        verify(stringRedisTemplate).delete("user:auth:" + userId);
    }
}
