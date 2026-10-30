package com.datn.financeapp.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình bean kết nối Cloudinary và nạp các thuộc tính từ file cấu hình.
 * <p>
 * Các hàm chính trong class:
 * <ul>
 *   <li>{@link #cloudinary(CloudinaryProperty)}: Khởi tạo và cung cấp singleton bean Cloudinary cho toàn ứng dụng.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperty.class)
public class CloudinaryConfig {

    /**
     * Khởi tạo Cloudinary client từ các thuộc tính cấu hình.
     *
     * @param properties cấu hình Cloudinary được nạp từ application.yml
     * @return đối tượng {@link Cloudinary} đã cấu hình
     */
    @Bean
    public Cloudinary cloudinary(CloudinaryProperty properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true
        ));
    }
}
