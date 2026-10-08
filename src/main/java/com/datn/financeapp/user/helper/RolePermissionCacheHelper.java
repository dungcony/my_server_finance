package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.mapper.RoleMapper;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper quản lý bộ nhớ đệm In-Memory (Local JVM) cho vai trò (Role) và quyền hạn (Permission).
 * <p>
 * Lưu trữ trực tiếp entity Role và Permission để triệt tiêu các câu truy vấn CSDL lặp lại
 * ở các luồng nghiệp vụ tạo người dùng, gán vai trò và phân quyền.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #findRoleByName}: Lấy Role theo tên enum (có cache In-Memory).</li>
 *   <li>{@link #findRoleById}: Lấy Role theo UUID (có cache In-Memory).</li>
 *   <li>{@link #findAllRoles}: Lấy danh sách toàn bộ các Role tồn tại trong hệ thống (có cache In-Memory).</li>
 *   <li>{@link #findRoleResponseByName}: Lấy thông tin RoleResponse theo tên enum từ cache In-Memory.</li>
 *   <li>{@link #findPermissionByName}: Lấy Permission theo tên enum (có cache In-Memory).</li>
 *   <li>{@link #findPermissionsByRole}: Lấy danh sách quyền hạn của một Role (có cache In-Memory).</li>
 *   <li>{@link #evictPermissionsByRole}: Hủy cache danh sách quyền của Role khi có thay đổi.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionCacheHelper {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    private final ConcurrentMap<RoleName, Role> rolesByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Role> rolesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<PermissionName, Permission> permissionsByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<RoleName, List<PermissionResponse>> permissionsByRole = new ConcurrentHashMap<>();
    private final AtomicReference<List<RoleResponse>> allRolesCache = new AtomicReference<>();

    public Optional<Role> findRoleByName(RoleName name) {
        if (name == null) {
            return Optional.empty();
        }
        Role cached = rolesByName.computeIfAbsent(name, n -> roleRepository.findByName(n).orElse(null));
        if (cached != null) {
            rolesById.putIfAbsent(cached.getId(), cached);
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public Optional<Role> findRoleById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        Role cached = rolesById.computeIfAbsent(id, i -> roleRepository.findById(i).orElse(null));
        if (cached != null) {
            rolesByName.putIfAbsent(cached.getName(), cached);
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public List<RoleResponse> findAllRoles() {
        List<RoleResponse> cached = allRolesCache.get();
        if (cached != null) {
            return cached;
        }
        List<RoleResponse> loaded = roleRepository.findAll()
                .stream()
                .map(roleMapper::toResponse)
                .toList();
        allRolesCache.set(loaded);
        return loaded;
    }

    public Optional<RoleResponse> findRoleResponseByName(RoleName roleName) {
        if (roleName == null) {
            return Optional.empty();
        }
        return findAllRoles().stream()
                .filter(r -> r.name() == roleName)
                .findFirst();
    }

    public Optional<Permission> findPermissionByName(PermissionName name) {
        if (name == null) {
            return Optional.empty();
        }
        Permission cached = permissionsByName.computeIfAbsent(name, n -> permissionRepository.findByName(n).orElse(null));
        return Optional.ofNullable(cached);
    }

    public List<PermissionResponse> findPermissionsByRole(RoleName roleName) {
        if (roleName == null) {
            return List.of();
        }
        return permissionsByRole.computeIfAbsent(roleName, n -> {
            Role role = roleRepository.findByName(n).orElse(null);
            return role != null ? roleMapper.mapPermissions(role) : List.of();
        });
    }

    public void evictPermissionsByRole(RoleName roleName) {
        if (roleName != null) {
            permissionsByRole.remove(roleName);
            allRolesCache.set(null);
        }
    }
}
