package com.datn.financeapp.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImgUploadTest {

    @Mock
    private Cloudinary cloudinary;

    private CloudinaryProperty properties;
    private ImgUpload imgUpload;

    @BeforeEach
    void setUp() {
        properties = new CloudinaryProperty(
                "test-cloud",
                "test-key",
                "test-secret",
                "test-folder",
                5 * 1024 * 1024
        );
        imgUpload = new ImgUpload(cloudinary, properties);
    }

    @Test
    @DisplayName("Upload thất bại khi file null")
    void upload_FileNull_ThrowsException() {
        assertThatThrownBy(() -> imgUpload.upload(null, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thất bại khi file rỗng")
    void upload_FileEmpty_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imgUpload.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thất bại khi kích thước file vượt ngưỡng")
    void upload_FileSizeExceeded_ThrowsException() {
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", largeBytes);

        assertThatThrownBy(() -> imgUpload.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thất bại khi Content-Type không hợp lệ")
    void upload_InvalidContentType_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "abc".getBytes());

        assertThatThrownBy(() -> imgUpload.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thất bại khi cấu hình Cloudinary chưa đủ")
    void upload_ConfigNotValid_ThrowsException() {
        CloudinaryProperty emptyProp = new CloudinaryProperty("", "", "", "", 1024);
        ImgUpload service = new ImgUpload(cloudinary, emptyProp);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "abc".getBytes());

        assertThatThrownBy(() -> service.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thành công trả về UploadRes với URL và ID")
    void upload_Success_ReturnsUploadRes() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image-content".getBytes());
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> uploadResult = Map.of(
                "secure_url", "https://res.cloudinary.com/test-cloud/image/upload/sample.png",
                "public_id", "test-folder/avatars/user-1"
        );
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        UploadRes res = imgUpload.upload(file, "avatars/", "user 1", false);

        assertThat(res).isNotNull();
        assertThat(res.url()).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/sample.png");
        assertThat(res.id()).isEqualTo("test-folder/avatars/user-1");
    }

    @Test
    @DisplayName("Upload thất bại khi Cloudinary trả về kết quả thiếu URL hoặc Public ID")
    void upload_InvalidResponseFromCloudinary_ThrowsException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image-content".getBytes());
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> uploadResult = Map.of("secure_url", "https://some-url.png");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        assertThatThrownBy(() -> imgUpload.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("Upload thất bại khi SDK Cloudinary ném IOException")
    void upload_IOException_ThrowsInternalError() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image-content".getBytes());
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> imgUpload.upload(file, "avatars", "u1", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("Delete bỏ qua khi publicId rỗng hoặc null")
    void delete_BlankPublicId_DoesNothing() {
        imgUpload.delete(null);
        imgUpload.delete("   ");

        verify(cloudinary, never()).uploader();
    }

    @Test
    @DisplayName("Delete thành công khi gọi Cloudinary destroy")
    void delete_Success_CallsDestroy() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("avatars/u1"), anyMap())).thenReturn(Map.of("result", "ok"));

        imgUpload.delete("avatars/u1");

        verify(uploader).destroy(eq("avatars/u1"), anyMap());
    }

    @Test
    @DisplayName("Delete thất bại khi SDK Cloudinary ném IOException")
    void delete_IOException_ThrowsInternalError() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("avatars/u1"), anyMap())).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> imgUpload.delete("avatars/u1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INTERNAL_ERROR.getCode());
    }
}
