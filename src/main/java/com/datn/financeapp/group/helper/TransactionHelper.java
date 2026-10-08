package com.datn.financeapp.group.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.mapper.GTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Helper hỗ trợ xử lý nghiệp vụ giao dịch nhóm (Group Transaction).
 * <p>
 * Đóng gói các logic tính toán thuần (Pure Math / Logic) bao gồm:
 * <ul>
 *     <li>Ánh xạ Entity sang DTO phản hồi chi tiết {@link GroupTransactionDetailRes}</li>
 *     <li>Tính toán mức biến động (delta) số dư quỹ nhóm theo loại giao dịch và nguồn tiền</li>
 *     <li>Phân giải danh sách người tham gia chia tiền (Participants) và kiểm tra tính hợp lệ</li>
 * </ul>
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #buildDetailRes}: Ánh xạ sang DTO. Input: entity giao dịch. Output: DTO chi tiết.</li>
 *   <li>{@link #buildParticipantsRes}: Ánh xạ danh sách người tham gia sang DTO. Input: entity giao dịch. Output: danh sách DTO người tham gia.</li>
 *   <li>{@link #calculateDelta}: Tính biến động quỹ. Input: loại giao dịch, nguồn tiền, số tiền, cờ reversal. Output: mức biến động (long).</li>
 *   <li>{@link #resolveParticipants}: Khởi tạo ds người tham gia. Input: tổng tiền, yêu cầu chia tiền, supplier ds thành viên. Output: ds entity participant.</li>
 *   <li>{@link #toParticipantEntities}: Chuyển DTO sang Entity. Input: ds DTO chia tiền. Output: ds entity participant.</li>
 *   <li>{@link #splitEvenly}: Chia đều tiền. Input: tổng tiền, ds user ID. Output: ds DTO kết quả chia tiền.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TransactionHelper {

    private final GTransactionMapper gTransactionMapper;

    /**
     * Ánh xạ đối tượng giao dịch sang DTO chi tiết.
     *
     * @param txn Thực thể giao dịch nhóm cần ánh xạ
     * @return DTO chi tiết giao dịch {@link GroupTransactionDetailRes}
     */
    public GroupTransactionDetailRes buildDetailRes(GTransaction txn) {
        return gTransactionMapper.toDetailResponse(txn);
    }

    /**
     * Ánh xạ danh sách người tham gia chia tiền của giao dịch sang DTO.
     *
     * @param txn Thực thể giao dịch nhóm
     * @return Danh sách DTO người tham gia giao dịch
     */
    public List<GroupTransactionParticipantRes> buildParticipantsRes(GTransaction txn) {
        List<TransactionParticipant> raw = txn.getParticipants();
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(gTransactionMapper::toParticipantResponse)
                .toList();
    }

    /**
     * Tính toán mức biến động (delta) của số dư quỹ nhóm sinh ra bởi giao dịch.
     *
     * @param type       Loại giao dịch ({@link GTransactionType})
     * @param source     Nguồn tiền sử dụng ({@link MoneySource}: {@code FUND} hoặc {@code PERSONAL})
     * @param amount     Số tiền giao dịch (phải &gt; 0)
     * @param isReversal Cờ đánh dấu có phải giao dịch đảo ngược (reversal/rollback) hay không
     * @return Mức biến động số dư quỹ (giá trị dương: quỹ tăng, âm: quỹ giảm, 0: quỹ không đổi)
     */
    public long calculateDelta(GTransactionType type, MoneySource source, long amount, boolean isReversal) {
        long factor = isReversal ? -1L : 1L;
        return switch (type) {
            case EXPENSE -> (source == MoneySource.FUND) ? -amount * factor : 0L;
            case CONTRIBUTION -> (source == MoneySource.PERSONAL) ? amount * factor : 0L;
            case REFUND, ADJUSTMENT_DOWN -> -amount * factor;
            case ADJUSTMENT_UP -> amount * factor;
        };
    }

    /**
     * Quyết định chiến lược chia tiền và khởi tạo danh sách người tham gia.
     *
     * @param amount           Tổng số tiền chi tiêu cần chia
     * @param paticipants      Danh sách yêu cầu người tham gia từ client, có thể null hoặc rỗng
     * @param allMemberInGroup Hàm cung cấp danh sách ID tất cả thành viên trong nhóm khi cần chia đều
     * @return Danh sách entity {@link TransactionParticipant} đã được tính toán số tiền chia cụ thể
     * @throws BusinessException Nếu dữ liệu chia tiền không đồng nhất hoặc tổng tiền chia không khớp
     */
    public List<TransactionParticipant> resolveParticipants(
            long amount,
            List<GroupTransactionParticipantReq> paticipants,
            Supplier<List<UUID>> allMemberInGroup
    ) {
        // không chỉ định ai thì chia đều cho toàn bộ thành viên nhóm
        if (paticipants == null || paticipants.isEmpty()) {
            List<UUID> memberIds = allMemberInGroup != null ? allMemberInGroup.get() : List.of();
            return toParticipantEntities(splitEvenly(amount, memberIds));
        }

        // chuyển đổi danh sách người tham gia sang entity với số tiền cụ thể
        return paticipants.stream()
                .map(p -> TransactionParticipant.builder()
                        .userId(p.userId())
                        .shareAmount(p.shareAmount())
                        .build())
                .toList();
    }

    /**
     * Chuyển đổi danh sách kết quả chia tiền dạng DTO sang danh sách entity {@link TransactionParticipant}.
     *
     * @param splitList Danh sách kết quả chia tiền
     * @return Danh sách entity người tham gia giao dịch
     */
    public List<TransactionParticipant> toParticipantEntities(List<GroupTransactionParticipantRes> splitList) {
        return splitList.stream()
                .map(s -> TransactionParticipant.builder()
                        .userId(s.userId())
                        .shareAmount(s.shareAmount())
                        .build())
                .toList();
    }

    /**
     * Chia đều tổng số tiền cho danh sách thành viên được chỉ định.
     *
     * @param totalAmount Tổng số tiền cần chia
     * @param userIds     Danh sách UUID của các thành viên tham gia chia tiền
     * @return Danh sách {@link GroupTransactionParticipantRes} chứa ID và số tiền chia tương ứng
     */
    public List<GroupTransactionParticipantRes> splitEvenly(long totalAmount, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        int n = userIds.size();
        long base = totalAmount / n;
        long rem = totalAmount % n;

        List<UUID> sorted = userIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        List<GroupTransactionParticipantRes> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long share = base + (i < rem ? 1 : 0);
            result.add(new GroupTransactionParticipantRes(sorted.get(i), share));
        }
        return result;
    }
}