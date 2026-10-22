package com.datn.financeapp.module.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Value = "{resource}:{action}", sinh tự động từ {@link Resource}/{@link Action} —
 * không gõ tay chuỗi để tránh lệch với dữ liệu seed trong DB (đã từng xảy ra).
 * Thêm quyền mới cho resource đã có: chỉ thêm 1 dòng enum, không đụng chỗ khác.
 */
@Getter
public enum PermissionName {
    USERS_READ(Resource.USERS, Action.READ, "Xem danh sách và chi tiết người dùng"),
    USERS_UPDATE(Resource.USERS, Action.UPDATE, "Khóa, mở khóa hoặc cập nhật thông tin người dùng"),
    USERS_DELETE(Resource.USERS, Action.DELETE, "Xoá tài khoản người dùng"),
    ROLES_READ(Resource.ROLES, Action.READ, "Xem danh sách vai trò và quyền hạn"),
    ROLES_UPDATE(Resource.ROLES, Action.UPDATE, "Sửa thông tin vai trò (tên, mô tả, cấp bậc)"),
    ROLES_DELETE(Resource.ROLES, Action.DELETE, "Xoá vai trò"),
    USER_ROLE_READ(Resource.USER_ROLE, Action.READ, "Xem vai trò đã gán cho người dùng"),
    USER_ROLE_UPDATE(Resource.USER_ROLE, Action.UPDATE, "Gán vai trò cho người dùng"),
    USER_ROLE_DELETE(Resource.USER_ROLE, Action.DELETE, "Thu hồi vai trò đã gán của người dùng"),
    ROLE_PERMISSION_READ(Resource.ROLE_PERMISSION, Action.READ, "Xem quyền hạn đã gán cho vai trò"),
    ROLE_PERMISSION_UPDATE(Resource.ROLE_PERMISSION, Action.UPDATE, "Gán quyền hạn cho vai trò"),
    ROLE_PERMISSION_DELETE(Resource.ROLE_PERMISSION, Action.DELETE, "Thu hồi quyền hạn đã gán của vai trò"),
    CATEGORIES_UPDATE(Resource.CATEGORIES, Action.UPDATE, "Tạo và cập nhật danh mục và biểu tượng mặc định hệ thống"),
    CATEGORIES_DELETE(Resource.CATEGORIES, Action.DELETE, "Xóa danh mục và biểu tượng mặc định hệ thống"),
    GROUP_READ(Resource.GROUP, Action.READ, "xem toàn bộ group"),
    ICONS_UPDATE(Resource.ICONS, Action.UPDATE, "Tạo và cập nhật biểu tượng hệ thống"),
    ICONS_DELETE(Resource.ICONS, Action.DELETE, "Xoá biểu tượng hệ thống"),
    RECURRING_TXN_READ(Resource.RECURRING_TXN, Action.READ, ""),
    RECURRING_TXN_DELETE(Resource.RECURRING_TXN, Action.DELETE, ""),
    WALLET_READ(Resource.WALLET, Action.READ, ""),
    WALLET_DELETE(Resource.WALLET, Action.DELETE, ""),
    WALLET_UPDATE(Resource.WALLET, Action.UPDATE, ""),
    TXN_READ(Resource.TXN, Action.READ, ""),
    TXN_DELETE(Resource.TXN, Action.DELETE, ""),
    TXN_UPDATE(Resource.TXN, Action.UPDATE, ""),
    GOAL_READ(Resource.GOAL, Action.READ, "Xem danh sách và chi tiết mục tiêu tiết kiệm"),
    GOAL_UPDATE(Resource.GOAL, Action.UPDATE, "Cập nhật mục tiêu tiết kiệm"),
    GOAL_DELETE(Resource.GOAL, Action.DELETE, "Xóa mục tiêu tiết kiệm"),
    BUDGET_READ(Resource.BUDGET, Action.READ, "Xem danh sách và chi tiết ngân sách toàn hệ thống"),
    BUDGET_DELETE(Resource.BUDGET, Action.DELETE, "Cưỡng chế xóa hoặc vô hiệu hóa ngân sách"),
    DEBT_READ(Resource.DEBT, Action.READ, "Xem danh sách, chi tiết và tổng quan sổ nợ toàn hệ thống"),
    DEBT_UPDATE(Resource.DEBT, Action.UPDATE, "Tạo nợ, trả nợ hộ, sửa nợ và xóa nợ khó đòi"),
    DEBT_DELETE(Resource.DEBT, Action.DELETE, "Xóa khoản nợ và hủy thanh toán nợ"),
    REPORT_READ(Resource.REPORT, Action.READ, "Xem danh sách và chi tiết tiến trình xuất báo cáo toàn hệ thống"),
    REPORT_DELETE(Resource.REPORT, Action.DELETE, "Hủy hoặc xóa tiến trình xuất báo cáo"),
    ;

    private final Resource resource;
    private final Action action;
    private final String description;

    PermissionName(Resource resource, Action action, String description) {
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    @JsonValue
    public String getValue() {
        return resource.getKey() + ":" + action.getKey();
    }

    private static final Map<String, PermissionName> VALUE_MAP =
            Stream.of(values()).collect(Collectors.toMap(PermissionName::getValue, Function.identity()));

    @JsonCreator
    public static PermissionName fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        PermissionName result = VALUE_MAP.get(value.trim().toLowerCase(Locale.ROOT));
        if (result != null) return result;
        try {
            return PermissionName.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown PermissionName: " + value);
        }
    }

    /**
     * Nhóm quyền theo tài nguyên — dùng để lọc/hiển thị permission theo từng resource.
     */
    public static List<PermissionName> byResource(Resource resource) {
        return Stream.of(values()).filter(p -> p.resource == resource).toList();
    }

    @Getter
    public enum Resource {
        USERS("users"),
        ROLES("roles"),
        USER_ROLE("user_role"),
        ROLE_PERMISSION("role_permission"),
        CATEGORIES("categories"),
        SYSTEM("system"),
        GROUP("group"),
        ICONS("icons"),
        RECURRING_TXN("recurring_txn"),
        WALLET("wallet"),
        TXN("transactions"),
        GOAL("goal"),
        BUDGET("budgets"),
        DEBT("debts"),
        REPORT("reports"),
        ;

        private final String key;

        Resource(String key) {
            this.key = key;
        }
    }

    @Getter
    public enum Action {
        READ("read"),
        UPDATE("update"),
        DELETE("delete"),
        ;

        private final String key;

        Action(String key) {
            this.key = key;
        }
    }

    /**
     * JPA converter lưu {@code value} ("users:read") vào cột varchar, không lưu tên enum ("USERS_READ").
     */
    @Converter(autoApply = true)
    public static class PermissionNameConverter implements AttributeConverter<PermissionName, String> {

        @Override
        public String convertToDatabaseColumn(PermissionName attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public PermissionName convertToEntityAttribute(String dbData) {
            return fromValue(dbData);
        }
    }
}
