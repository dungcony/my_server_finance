package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Helper quản lý bộ nhớ đệm Redis Hash cho thông tin bảo mật và phân quyền của người dùng.
 * <p>
 * Lưu trữ đồng thời cấp bậc vai trò mạnh nhất (top role level) và danh sách quyền hạn (authorities)
 * dưới dạng Redis Hash (key: {@code user:auth:{userId}}) để triệt tiêu các câu truy vấn CSDL lặp lại trong module {@code user}.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #getUserLevel}: Lấy level mạnh nhất của người dùng từ Redis Hash field "level".</li>
 *   <li>{@link #getUserAuthorities}: Lấy danh sách quyền hạn và vai trò từ Redis Hash field "authorities".</li>
 *   <li>{@link #evictUserAuth}: Xóa sạch toàn bộ cache (level và authorities) của người dùng khi có thay đổi.</li>
 *   <li>{@link #evictUserLevel}: Hàm bí danh gọi sang {@link #evictUserAuth}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class UserAuthCacheHelper {

    private static final String KEY_PREFIX = "user:auth:";
    private static final String FIELD_LEVEL = "level";
    private static final String FIELD_AUTHORITIES = "authorities";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public UserAuthCacheHelper(UserRepository userRepository, StringRedisTemplate stringRedisTemplate) {
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // constructor hỗ trợ unit test khi không truyền redis template
    public UserAuthCacheHelper(UserRepository userRepository) {
        this(userRepository, null);
    }

    public int getUserLevel(UUID userId) {
        if (userId == null) {
            return Integer.MAX_VALUE;
        }

        String key = KEY_PREFIX + userId;

        if (stringRedisTemplate != null) {
            try {
                Object cached = stringRedisTemplate.opsForHash().get(key, FIELD_LEVEL);
                if (cached != null) {
                    return Integer.parseInt(cached.toString());
                }
            } catch (Exception e) {
                log.warn("Lỗi đọc cache user level: {}", e.getMessage());
            }
        }

        int level = userRepository.findTopRoleLevelByUserId(userId);

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForHash().put(key, FIELD_LEVEL, String.valueOf(level));
                stringRedisTemplate.expire(key, TTL);
            } catch (Exception e) {
                log.warn("Lỗi ghi cache user level: {}", e.getMessage());
            }
        }

        return level;
    }

    public List<String> getUserAuthorities(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        String key = KEY_PREFIX + userId;

        if (stringRedisTemplate != null) {
            try {
                Object cached = stringRedisTemplate.opsForHash().get(key, FIELD_AUTHORITIES);
                if (cached != null) {
                    String authStr = cached.toString();
                    if (authStr.isEmpty()) {
                        return Collections.emptyList();
                    }
                    return Arrays.asList(authStr.split(","));
                }
            } catch (Exception e) {
                log.warn("Lỗi đọc cache user authorities: {}", e.getMessage());
            }
        }

        List<String> authorities = userRepository.findAuthoritiesByUserId(userId);

        if (stringRedisTemplate != null) {
            try {
                String authStr = authorities != null ? String.join(",", authorities) : "";
                stringRedisTemplate.opsForHash().put(key, FIELD_AUTHORITIES, authStr);
                stringRedisTemplate.expire(key, TTL);
            } catch (Exception e) {
                log.warn("Lỗi ghi cache user authorities: {}", e.getMessage());
            }
        }

        return authorities != null ? authorities : Collections.emptyList();
    }

    public void evictUserAuth(UUID userId) {
        if (stringRedisTemplate != null && userId != null) {
            try {
                stringRedisTemplate.delete(KEY_PREFIX + userId);
            } catch (Exception e) {
                log.warn("Lỗi xóa cache user auth: {}", e.getMessage());
            }
        }
    }

    public void evictUserLevel(UUID userId) {
        evictUserAuth(userId);
    }
}
