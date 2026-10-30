package com.datn.financeapp.common.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // tên tham số trang trên URL: ?page=1
    private static final String PAGE_PARAM = "page";
    // tên tham số kích thước trang trên URL: ?page_size=20 (snake_case theo quy ước query param, rule 2.8)
    private static final String SIZE_PARAM = "page_size";
    // số dòng mỗi trang khi client không gửi page_size
    private static final int DEFAULT_PAGE_SIZE = 20;
    // client gửi page_size lớn hơn thì bị cắt về giá trị này, tránh kéo quá nhiều dòng một lần
    private static final int MAX_PAGE_SIZE = 100;
    // sắp xếp khi client không gửi sort: mới tạo lên đầu; mọi entity dùng Pageable phải có thuộc tính createdAt
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    // Bean này thay thế customizer mặc định của Spring Boot (@ConditionalOnMissingBean),
    // nên mọi cấu hình phân trang đều đặt ở đây, không đọc spring.data.web.pageable.* trong yml
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setPageParameterName(PAGE_PARAM);
            resolver.setSizeParameterName(SIZE_PARAM);
            // true: client gửi page=1 là trang đầu (khớp PageMeta.from trả page + 1); false: page=0 là trang đầu
            resolver.setOneIndexedParameters(true);
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
            // dùng khi client không gửi page/page_size/sort: trang đầu, DEFAULT_PAGE_SIZE dòng, sort mặc định
            resolver.setFallbackPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE, DEFAULT_SORT));
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Đường dẫn thân thiện /app để dễ tương tác theo yêu cầu:
        // /app -> /ui/user/dashboard.html
        registry.addRedirectViewController("/app", "/ui/user/dashboard.html");
        registry.addRedirectViewController("/app/", "/ui/user/dashboard.html");
        registry.addRedirectViewController("/app/user", "/ui/user/dashboard.html");
        registry.addRedirectViewController("/app/user/", "/ui/user/dashboard.html");

        // /app/admin -> /ui/admin/dashboard.html
        registry.addRedirectViewController("/app/admin", "/ui/admin/dashboard.html");
        registry.addRedirectViewController("/app/admin/", "/ui/admin/dashboard.html");

        // /app/manager hoặc /app/groups -> /ui/manager/dashboard.html hoặc /ui/manager/groups.html
        registry.addRedirectViewController("/app/manager", "/ui/manager/dashboard.html");
        registry.addRedirectViewController("/app/manager/", "/ui/manager/dashboard.html");
        registry.addRedirectViewController("/app/groups", "/ui/manager/groups.html");
        registry.addRedirectViewController("/app/groups/", "/ui/manager/groups.html");

        // /app/login -> /ui/login.html
        registry.addRedirectViewController("/app/login", "/ui/login.html");
        registry.addRedirectViewController("/app/login/", "/ui/login.html");
    }
}
