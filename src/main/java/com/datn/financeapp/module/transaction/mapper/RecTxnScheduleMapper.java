package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleRes;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapper chuyển đổi giữa entity {@link RecTxnSchedule} và các DTO liên quan.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecTxnScheduleMapper {

    /**
     * Chuyển đổi thực thể buổi lịch sang DTO phản hồi chi tiết.
     *
     * @param schedule thực thể buổi lịch phát sinh
     * @return DTO phản hồi chi tiết
     */
    @Mapping(target = "recurringId", source = "recTxn.id")
    RecTxnScheduleRes toResponse(RecTxnSchedule schedule);

    /**
     * Chuyển đổi danh sách thực thể sang danh sách DTO.
     *
     * @param schedules danh sách thực thể
     * @return danh sách DTO phản hồi
     */
    List<RecTxnScheduleRes> toResponseList(List<RecTxnSchedule> schedules);

    /**
     * Chuyển đổi yêu cầu tạo buổi lịch thành thực thể {@link RecTxnSchedule}.
     *
     * @param req yêu cầu tạo buổi lịch
     * @return thực thể buổi lịch
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "recTxn", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RecTxnSchedule toEntity(RecTxnScheduleCreateReq req);
}
