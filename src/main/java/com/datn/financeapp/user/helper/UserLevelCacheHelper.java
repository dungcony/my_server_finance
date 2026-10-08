package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Helper quản lý bộ nhớ đệm Redis cho cấp bậc vai trò mạnh nhất (top role level) của người dùng.
 * <p>
 * Giúp triệt tiêu các câu truy vấn CSDL lặp lại khi kiểm tra phân cấp quản trị giữa người gọi và
 * người bị thao tác trong module {@code user}.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #getUserLevel}: Lấy level mạnh nhất của người dùng (có cache Redis).</li>
 *   <li>{@link #evictUserLevel}: Xóa cache level khi người dùng được gán hoặc thu hồi vai trò.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class UserLevelCacheHelper {

    private static final String KEY_PREFIX = "user:level:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public UserLevelCacheHelper(UserRepository userRepository, StringRedisTemplate stringRedisTemplate) {
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // constructor hỗ trợ unit test khi không truyền redis template
    public UserLevelCacheHelper(UserRepository userRepository) {
        this(userRepository, null);
    }

    public int getUserLevel(UUID userId) {
        if (userId == null) {
            return Integer.MAX_VALUE;
        }

        if (stringRedisTemplate != null) {
            try {
                String cached = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
                if (cached != null) {
                    return Integer.parseInt(cached);
                }
            } catch (Exception e) {
                log.warn("Lỗi đọc cache user level: {}", e.getMessage());
            }
        }

        int level = userRepository.findTopRoleLevelByUserId(userId);

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, String.valueOf(level), TTL);
            } catch (Exception e) {
                log.warn("Lỗi ghi cache user level: {}", e.getMessage());
            }
        }

        return level;
    }

    public void evictUserLevel(UUID userId) {
        if (stringRedisTemplate != null && userId != null) {
            try {
                stringRedisTemplate.delete(KEY_PREFIX + userId);
            } catch (Exception e) {
                log.warn("Lỗi xóa cache user level: {}", e.getMessage());
            }
        }
    }
}
