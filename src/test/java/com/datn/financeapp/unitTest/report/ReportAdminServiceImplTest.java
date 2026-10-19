package com.datn.financeapp.unitTest.report;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.report.dto.request.ExportJobFilterRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import com.datn.financeapp.module.report.entity.ExportJob;
import com.datn.financeapp.module.report.mapper.ReportMapper;
import com.datn.financeapp.module.report.repository.ExportJobRepository;
import com.datn.financeapp.module.report.service.ReportQueryService;
import com.datn.financeapp.module.report.service.impl.ReportAdminServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAdminServiceImplTest {

    @Mock
    private ExportJobRepository exportJobRepository;

    @Mock
    private ReportQueryService reportQueryService;

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportAdminServiceImpl reportAdminService;

    private final UUID jobId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("searchJobs: Admin tra cứu danh sách tiến trình xuất báo cáo phân trang")
    void searchJobs_Success() {
        ExportJob job = ExportJob.builder().id(jobId).userId(userId).format("csv").status("processing").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(exportJobRepository.searchByAdmin(userId, "processing", "csv", pageable))
                .thenReturn(new PageImpl<>(List.of(job)));

        ExportJobResponse res = new ExportJobResponse(jobId, "processing", null, null, null);
        when(reportMapper.toExportJobResponse(job)).thenReturn(res);

        ExportJobFilterRequest filter = new ExportJobFilterRequest(userId, "processing", "csv");
        Page<ExportJobResponse> page = reportAdminService.searchJobs(filter, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).jobId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("getJobDetail: Lấy chi tiết job thành công")
    void getJobDetail_Success() {
        ExportJob job = ExportJob.builder().id(jobId).userId(userId).format("csv").status("completed").build();
        when(exportJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        ExportJobResponse res = new ExportJobResponse(jobId, "completed", "/download", null, null);
        when(reportMapper.toExportJobResponse(job)).thenReturn(res);

        ExportJobResponse detail = reportAdminService.getJobDetail(jobId);

        assertThat(detail).isNotNull();
        assertThat(detail.jobId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("getJobDetail: Không tìm thấy job ném NOT_FOUND")
    void getJobDetail_NotFound_ThrowsException() {
        when(exportJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportAdminService.getJobDetail(jobId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("deleteJob: Admin xóa job và tệp vật lý thành công")
    void deleteJob_Success() throws IOException {
        File temp = File.createTempFile("delete-test-", ".csv");
        ExportJob job = ExportJob.builder().id(jobId).filePath(temp.getAbsolutePath()).build();
        when(exportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        reportAdminService.deleteJob(jobId);

        verify(exportJobRepository).delete(job);
        assertThat(temp.exists()).isFalse();
    }

    @Test
    @DisplayName("getUserSummary: Admin xem tóm tắt báo cáo của bất kỳ user nào")
    void getUserSummary_Success() {
        ReportSummaryResponse expected = new ReportSummaryResponse(
                new ReportSummaryResponse.PeriodInfo("Tháng này", LocalDate.now(), LocalDate.now()),
                0L, 100L, 200L, 100L, 100L, 1L, 100L, null);
        when(reportQueryService.summary(userId, "month", null, null, null)).thenReturn(expected);

        ReportSummaryResponse actual = reportAdminService.getUserSummary(userId, "month", null, null, null);

        assertThat(actual).isNotNull();
        assertThat(actual.netIncome()).isEqualTo(100L);
    }
}
