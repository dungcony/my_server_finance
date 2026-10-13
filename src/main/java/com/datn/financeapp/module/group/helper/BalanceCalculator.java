package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;


/**
 * Class chuyên dùng để tính ảnh hưởng của giao dịch nhóm lên số dư từng thành viên.
 * Giúp trả lời các câu hỏi: Ai đã góp/ứng bao nhiêu? Ai được trả lại bao nhiêu? Ai phải gánh bao nhiêu?
 */
public final class BalanceCalculator {

    /**
     * Tính ảnh hưởng của một giao dịch nhóm đã lưu lên số dư từng thành viên
     *
     * @param txn giao dịch nhóm
     * @return Ảnh hưởng theo từng thành viên, đối tượng mới mỗi lần gọi
     */
    public static MemberBalances effectOf(GroupTxn txn) {
        return effectOf(txn.getTransactorId(), txn.getAmount(), txn.getMoneySource(), txn.getType(),
                txn.getParticipants());
    }

    /**
     * Tính ảnh hưởng của một giao dịch lên số dư từng thành viên
     *
     * @param transactorId người ứng tiền cho nhóm (nộp quỹ hoặc chi hộ bằng tiền cá nhân, cả hai đều tính vào contribution), null khi dùng tiền quỹ
     * @param amount       số tiền giao dịch
     * @param moneySource  nguồn tiền (FUND hoặc PERSONAL), chỉ ảnh hưởng tới EXPENSE: PERSONAL thì cộng contribution cho transactorId
     * @param type         loại giao dịch, quyết định chỉ số nào được cộng: CONTRIBUTION cộng contribution, REFUND cộng refund, EXPENSE và ADJUSTMENT_DOWN cộng share, ADJUSTMENT_UP trừ share
     * @param participants những người được chia tiền, dùng để tính share cho EXPENSE, ADJUSTMENT_* và phần được nhận cho REFUND
     * @return Ảnh hưởng theo từng thành viên, đối tượng mới mỗi lần gọi
     */
    public static MemberBalances effectOf(
            UUID transactorId,
            long amount,
            MoneySource moneySource,
            GroupTxnType type,
            List<GroupTxnParticipant> participants
    ) {

        MemberBalances res = new MemberBalances();

        switch (type) {
            case CONTRIBUTION -> res.addContribution(transactorId, amount);
            case EXPENSE -> {
                if (moneySource == MoneySource.PERSONAL) {
                    // cộng tiền túi cho người đã trả tiền
                    res.addContribution(transactorId, amount);
                }
                addParticipantShares(participants, 1L, res);
            }
            case REFUND -> participants.forEach(p -> res.addRefund(p.getUserId(), p.getShareAmount()));
            case ADJUSTMENT_DOWN -> addParticipantShares(participants, 1L, res);
            case ADJUSTMENT_UP -> addParticipantShares(participants, -1L, res);
        }
        return res;
    }

    // ---------------------------------------PRIVATE----------------------------------------------//

    /**
     * cộng thẳng phần chia đã ghi trong group_transaction_participants vào share của từng người
     *
     * @param participants những người được tính
     * @param factor       +1: tăng, -1: giảm
     * @param balances     số dư theo từng thành viên, hàm cộng dồn trực tiếp vào đối tượng này qua {@link MemberBalances#addShare(UUID, long)}
     */
    private static void addParticipantShares(
            @NonNull List<GroupTxnParticipant> participants,
            long factor,
            MemberBalances balances
    ) {
        for (GroupTxnParticipant p : participants) {
            balances.addShare(p.getUserId(), p.getShareAmount() * factor);
        }
    }


}
