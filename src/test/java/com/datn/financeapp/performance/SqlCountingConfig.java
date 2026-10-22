package com.datn.financeapp.performance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

/**
 * Bọc {@link DataSource} bằng một proxy đếm số câu SQL ở tầng JDBC, dùng cho
 * {@link EndpointSqlCountIntegrationTest}.
 *
 * <p>Đếm ở tầng JDBC thay vì dùng thống kê của Hibernate để tính cả câu SQL đi qua
 * {@code JdbcTemplate} (các điểm nóng như báo cáo và cập nhật số dư viết bằng native SQL),
 * những câu mà bộ đếm Hibernate không thấy.
 *
 * <p>Mỗi lần gọi {@code prepareStatement}/{@code prepareCall}/{@code createStatement} trên
 * connection tính là một câu SQL. Bộ đếm là toàn cục nên các tiến trình nền cùng chạy trong lúc đo
 * có thể làm số liệu cao hơn thực tế một chút.
 */
@TestConfiguration
public class SqlCountingConfig {

    private static final Set<String> COUNTED_METHODS = Set.of("prepareStatement", "prepareCall", "createStatement");

    private static final AtomicLong COUNT = new AtomicLong();

    public static long sqlCount() {
        return COUNT.get();
    }

    @Bean
    static BeanPostProcessor sqlCountingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || Proxy.isProxyClass(bean.getClass()))
                    return bean;
                return Proxy.newProxyInstance(
                        bean.getClass().getClassLoader(),
                        ClassUtils.getAllInterfaces(bean),
                        dataSourceHandler(dataSource));
            }
        };
    }

    private static InvocationHandler dataSourceHandler(DataSource target) {
        return (proxy, method, args) -> {
            Object result = invoke(target, method, args);
            if (method.getName().equals("getConnection") && result instanceof Connection connection)
                return countingConnection(connection);
            return result;
        };
    }

    private static Connection countingConnection(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (COUNTED_METHODS.contains(method.getName()))
                        COUNT.incrementAndGet();
                    return invoke(target, method, args);
                });
    }

    // bóc InvocationTargetException để người gọi nhận đúng SQLException gốc
    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
