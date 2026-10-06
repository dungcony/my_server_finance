package com.datn.financeapp.group.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.FundRepository;
import com.datn.financeapp.group.repository.MemberBalanceRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberBalanceServiceImplTest {

    @Mock
    private MemberBalanceRepository memberBalanceRepository;

    @Mock
    private FundRepository fundRepository;

    private MemberBalanceServiceImpl service;

    private final UUID groupId = UUID.randomUUID();

    // ba id cố định để biết trước thứ tự theo UUID.compareTo
    private final UUID userA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID userB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID userC = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @BeforeEach
    void setUp() {
        service = new MemberBalanceServiceImpl(memberBalanceRepository, fundRepository);
    }

    private MemberBalances contributions(Object... userAndAmount) {
        Map<UUID, MemberBalanceAccumulator> map = new HashMap<>();
        for (int i = 0; i < userAndAmount.length; i += 2) {
            MemberBalanceAccumulator acc = new MemberBalanceAccumulator();
            acc.addContribution((Long) userAndAmount[i + 1]);
            map.put((UUID) userAndAmount[i], acc);
        }
        return new MemberBalances(map);
    }

    @Test
    @DisplayName("Chỉ ghi chênh lệch cho người có thay đổi, theo đúng thứ tự user_id")
    void applyDelta_writesOnlyChangedMembersInUserIdOrder() {
        MemberBalances before = contributions(userA, 100_000L, userB, 50_000L);
        MemberBalances after = contributions(userC, 20_000L, userB, 80_000L, userA, 100_000L);

        service.applyDelta(groupId, before, after);

        InOrder order = inOrder(memberBalanceRepository);
        order.verify(memberBalanceRepository).addDelta(groupId, userB, 0L, 30_000L, 0L, 0L);
        order.verify(memberBalanceRepository).addDelta(groupId, userC, 0L, 20_000L, 0L, 0L);
        verify(memberBalanceRepository, never())
                .addDelta(eq(groupId), eq(userA), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Giao dịch bị xoá: ghi phần trừ đúng bằng ảnh hưởng cũ")
    void applyDelta_removedTransactionWritesNegativeDelta() {
        MemberBalances before = contributions(userA, 100_000L);
        MemberBalances after = contributions();

        service.applyDelta(groupId, before, after);

        verify(memberBalanceRepository).addDelta(groupId, userA, 0L, -100_000L, 0L, 0L);
    }

    @Test
    @DisplayName("Không có chênh lệch thì không ghi gì")
    void applyDelta_noChange_writesNothing() {
        service.applyDelta(groupId, contributions(userA, 100_000L), contributions(userA, 100_000L));

        verify(memberBalanceRepository, never())
                .addDelta(any(), any(), anyLong(), anyLong(), anyLong(), anyLong());
    }
}
