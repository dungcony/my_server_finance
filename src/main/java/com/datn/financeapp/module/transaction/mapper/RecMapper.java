package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnRes;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

/**
 * Mapper chuyển đổi giữa entity {@link RecTxn} và các DTO liên quan.
 * <p>
 * Cung cấp các phương thức ánh xạ:
 * <ul>
 *   <li>{@link #toDetalRes(RecTxn, CateSumaryRes, WalletSumaryRes)}: Chuyển đổi sang DTO chi tiết.</li>
 *   <li>{@link #toEntity(RecCreateReq, UUID, UUID)}: Chuyển đổi yêu cầu tạo thành thực thể.</li>
 * </ul>
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecMapper {

    /**
     * Chuyển đổi thông tin cấu hình giao dịch định kỳ cùng thông tin ví và danh mục thành DTO chi tiết.
     *
     * @param recTxn          thực thể cấu hình giao dịch định kỳ
     * @param cateSumaryRes   thông tin tóm tắt danh mục
     * @param walletSumaryRes thông tin tóm tắt ví tiền
     * @return DTO phản hồi chi tiết giao dịch định kỳ
     */
    @Mapping(target = "id", source = "recTxn.id")
    @Mapping(target = "displayName", source = "recTxn.name")
    @Mapping(target = "wallet", source = "walletSumaryRes")
    @Mapping(target = "category", source = "cateSumaryRes")
    @Mapping(target = "startDate", source = "recTxn.startDate")
    @Mapping(target = "endDate", source = "recTxn.endDate")
    @Mapping(target = "frequencyType", source = "recTxn.frequency")
    @Mapping(target = "interval", source = "recTxn.interval")
    @Mapping(target = "isEnabled", source = "recTxn.isEnabled")
    @Mapping(target = "type", source = "recTxn.type")
    @Mapping(target = "amount", source = "recTxn.amount")
    @Mapping(target = "note", source = "recTxn.note")
    RecTxnRes toDetalRes(
            RecTxn recTxn,
            CateSumaryRes cateSumaryRes,
            WalletSumaryRes walletSumaryRes
    );

    /**
     * Chuyển đổi yêu cầu tạo giao dịch định kỳ thành thực thể {@link RecTxn}.
     *
     * @param req    yêu cầu tạo mới giao dịch định kỳ
     * @param id     ID của giao dịch định kỳ
     * @param userId ID người dùng sở hữu
     * @return thực thể cấu hình giao dịch định kỳ
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "name", source = "req.displayName")
    @Mapping(target = "startDate", source = "req.startDate")
    @Mapping(target = "endDate", source = "req.endDate")
    @Mapping(target = "walletId", source = "req.walletId")
    @Mapping(target = "categoryCode", source = "req.categoryCode")
    @Mapping(target = "type", source = "req.type")
    @Mapping(target = "amount", source = "req.amount")
    @Mapping(target = "frequency", source = "req.frequency")
    @Mapping(target = "interval", source = "req.interval")
    @Mapping(target = "note", source = "req.note")
    @Mapping(target = "isEnabled", constant = "true")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RecTxn toEntity(RecCreateReq req, UUID id, UUID userId);
}
