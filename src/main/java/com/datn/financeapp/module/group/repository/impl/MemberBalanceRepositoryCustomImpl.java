package com.datn.financeapp.module.group.repository.impl;

import com.datn.financeapp.module.group.helper.MemberBalances;
import com.datn.financeapp.module.group.repository.MemberBalanceRepositoryCustom;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MemberBalanceRepositoryCustomImpl implements MemberBalanceRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL_BATCH_UPSERT = """
            INSERT INTO group_member_balances (group_id, user_id, contribution, refund, share)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (group_id, user_id) DO UPDATE SET
                contribution = group_member_balances.contribution + EXCLUDED.contribution,
                refund       = group_member_balances.refund + EXCLUDED.refund,
                share        = group_member_balances.share + EXCLUDED.share
            """;

    @Override
    public void batchAddDelta(UUID groupId, Map<UUID, MemberBalances.Balance> deltas) {
        // lọc những thành viên có thay đổi số dư thực tế
        List<Map.Entry<UUID, MemberBalances.Balance>> pending = deltas.entrySet().stream()
                .filter(e -> !e.getValue().isZero())
                .toList();

        if (pending.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(SQL_BATCH_UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {

                Map.Entry<UUID, MemberBalances.Balance> entry = pending.get(i);

                UUID userId = entry.getKey();

                MemberBalances.Balance delta = entry.getValue();

                ps.setObject(1, groupId);
                ps.setObject(2, userId);
                ps.setLong(3, delta.contribution());
                ps.setLong(4, delta.refund());
                ps.setLong(5, delta.share());
            }

            @Override
            public int getBatchSize() {
                return pending.size();
            }
        });
    }
}
