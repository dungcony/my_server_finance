package com.datn.financeapp.unitTest.report;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.report.dto.request.ExportRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.entity.ExportJob;
import com.datn.financeapp.module.report.export.ExportAsyncRunner;
import com.datn.financeapp.module.report.mapper.ReportMapper;
import com.datn.financeapp.module.report.repository.ExportJobRepository;
import com.datn.financeapp.module.report.service.impl.ExportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceImplTest {

    @Mock
    private ExportJobRepository exportJobRepository;

    @Mock
    private ExportAsyncRunner exportAsyncRunner;

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ExportServiceImpl exportService;

    private final UUID userId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @Test
    @DisplayName("createJob: Định dạng không phải csv sẽ ném EXPORT_FORMAT_NOT_SUPPORTED")
    void createJob_NonCsv_ThrowsException() {
        ExportRequest req = new ExportRequest("pdf", "month", null, null, "all");

        assertThatThrownBy(() -> exportService.createJob(userId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.EXPORT_FORMAT_NOT_SUPPORTED.getCode()));
    }

    @Test
    @DisplayName("getStatus: Không tìm thấy job hoặc job của user khác ném NOT_FOUND")
    void getStatus_NotFound_ThrowsException() {
        when(exportJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.getStatus(userId, jobId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("getStatus: Trả về trạng thái hợp lệ khi tìm thấy job")
    void getStatus_Success() {
        ExportJob job = ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("completed")
                .build();
        when(exportJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
        ExportJobResponse expected = new ExportJobResponse(jobId, "completed", "/reports/export/" + jobId + "/download", null, null);
        when(reportMapper.toExportJobResponse(job)).thenReturn(expected);

        ExportJobResponse actual = exportService.getStatus(userId, jobId);

        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("download: Link tải hết hạn ném EXPORT_LINK_EXPIRED")
    void download_Expired_ThrowsException() {
        ExportJob job = ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("completed")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(exportJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> exportService.download(userId, jobId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.EXPORT_LINK_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("download: Tải file thành công khi job hợp lệ và chưa hết hạn")
    void download_Success() throws IOException {
        File tempFile = File.createTempFile("export-test-", ".csv");
        tempFile.deleteOnExit();

        ExportJob job = ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("completed")
                .filePath(tempFile.getAbsolutePath())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(exportJobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        Resource resource = exportService.download(userId, jobId);

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }
}
