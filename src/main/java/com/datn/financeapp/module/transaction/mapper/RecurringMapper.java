package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.response.RecDetailRes;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper chuyển đổi giữa entity {@link RecTxn} và các DTO liên quan.
 * <p>
 * Cung cấp các phương thức ánh xạ:
 * <ul>
 *   <li>{@link #toDetalRes(RecTxn, CateSumaryRes, WalletSumaryRes)}: Chuyển đổi sang DTO chi tiết.</li>
 *   <li>{@link #toEntity(RecCreateReq)}: Chuyển đổi yêu cầu tạo thành thực thể.</li>
 * </ul>
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecurringMapper {

    /**
     * Chuyển đổi thông tin cấu hình giao dịch định kỳ cùng thông tin ví và danh mục thành DTO chi tiết.
     *
     * @param recTxn          thực thể cấu hình giao dịch định kỳ
     * @param cateSumaryRes   thông tin tóm tắt danh mục
     * @param walletSumaryRes thông tin tóm tắt ví tiền
     * @return DTO phản hồi chi tiết giao dịch định kỳ
     */
    @Mapping(target = "id", source = "recurringTransaction.id")
    @Mapping(target = "displayName", source = "recurringTransaction.name")
    @Mapping(target = "wallet", source = "walletSumaryRes")
    @Mapping(target = "category", source = "cateSumaryRes")
    RecDetailRes toDetalRes(
            RecTxn recTxn,
            CateSumaryRes cateSumaryRes,
            WalletSumaryRes walletSumaryRes
    );

    /**
     * Chuyển đổi yêu cầu tạo giao dịch định kỳ thành thực thể {@link RecTxn}.
     *
     * @param req yêu cầu tạo mới giao dịch định kỳ
     * @return thực thể cấu hình giao dịch định kỳ
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "name", source = "displayName")
    @Mapping(target = "startDate", source = "startAt")
    @Mapping(target = "endDate", source = "endAt")
    @Mapping(target = "isEnabled", constant = "true")
    @Mapping(target = "isDeleted", ignore = true)
    RecTxn toEntity(RecCreateReq req);
}
