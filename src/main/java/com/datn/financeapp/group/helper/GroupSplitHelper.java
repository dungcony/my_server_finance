package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GroupSplitHelper {

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
