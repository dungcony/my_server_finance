package com.datn.financeapp.common.configs;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class WebConfig {

    // Bean này thay thế customizer mặc định của Spring Boot (@ConditionalOnMissingBean)
    // nên phải tự áp lại cấu hình spring.data.web.pageable.* trong application.yml
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(SpringDataWebProperties properties) {
        SpringDataWebProperties.Pageable pageable = properties.getPageable();
        return resolver -> {
            resolver.setPageParameterName(pageable.getPageParameter());
            resolver.setSizeParameterName(pageable.getSizeParameter());
            resolver.setOneIndexedParameters(pageable.isOneIndexedParameters());
            resolver.setPrefix(pageable.getPrefix());
            resolver.setQualifierDelimiter(pageable.getQualifierDelimiter());
            resolver.setMaxPageSize(pageable.getMaxPageSize());
            resolver.setFallbackPageable(
                    PageRequest.of(0, pageable.getDefaultPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"))
            );
        };
    }
}
