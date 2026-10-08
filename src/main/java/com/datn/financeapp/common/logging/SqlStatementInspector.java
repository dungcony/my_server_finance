package com.datn.financeapp.common.logging;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Bộ chặn (inspector) câu lệnh SQL của Hibernate kết hợp {@link ThreadLocal} để đếm số câu lệnh
 * SQL được thực thi riêng biệt cho từng luồng (thread).
 * <p>
 * Class cung cấp các hàm quản lý vòng đời đo đếm theo độ sâu gọi:
 * <ul>
 *   <li>{@link #enter()}: Bắt đầu một phạm vi đo hoặc tăng độ sâu gọi khi có method lồng nhau.</li>
 *   <li>{@link #exit()}: Giảm độ sâu gọi và tự động dọn dẹp {@link ThreadLocal} khi thoát khỏi method ngoài cùng.</li>
 *   <li>{@link #getCount()}: Lấy số lượng câu SQL đã được chuẩn bị trên luồng hiện tại.</li>
 *   <li>{@link #inspect(String)}: Nhận diện câu SQL từ Hibernate và tăng biến đếm nếu đang trong phạm vi đo.</li>
 *   <li>{@link #customize(Map)}: Đăng ký inspector vào cấu hình Hibernate của Spring Boot.</li>
 * </ul>
 * </p>
 */
@Component
@Profile("dev")
public class SqlStatementInspector implements StatementInspector, HibernatePropertiesCustomizer {

    private final ThreadLocal<Long> sqlCount = ThreadLocal.withInitial(() -> 0L);
    private final ThreadLocal<Integer> callDepth = ThreadLocal.withInitial(() -> 0);

    // tăng biến đếm nếu luồng hiện tại đang nằm trong phạm vi đo của service
    @Override
    public String inspect(String sql) {
        if (callDepth.get() > 0) {
            sqlCount.set(sqlCount.get() + 1);
        }
        return sql;
    }

    // lấy số lượng câu SQL đã chạy trên luồng hiện tại
    public long getCount() {
        return sqlCount.get();
    }

    // bắt đầu ngữ cảnh đo hoặc tăng độ sâu gọi lồng nhau
    public void enter() {
        callDepth.set(callDepth.get() + 1);
    }

    // giảm độ sâu gọi và dọn dẹp thread local nếu đã thoát ra ngoài cùng
    public void exit() {
        int depth = callDepth.get() - 1;
        if (depth <= 0) {
            callDepth.remove();
            sqlCount.remove();
        } else {
            callDepth.set(depth);
        }
    }

    // đăng ký inspector với Hibernate session factory
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, this);
    }
}
