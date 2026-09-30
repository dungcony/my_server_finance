import re

with open('src/test/java/com/datn/financeapp/group/helper/BalanceCalculatorTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Remove calculator instance
text = re.sub(r'private BalanceCalculator calculator;\s*@BeforeEach\s*void setUp\(\) \{\s*calculator = new BalanceCalculator\(\);\s*\}', '', text)
text = text.replace('calculator.calculateBalances', 'BalanceCalculator.calculateBalances')
text = text.replace('import com.datn.financeapp.group.entity.TransactionParticipant;', 'import com.datn.financeapp.group.entity.TransactionParticipant;\nimport com.datn.financeapp.group.entity.Member;')
text = text.replace('import java.util.Map;', 'import java.util.Map;\nimport java.util.Collections;')

# 2. Fix testScenario1_ThreeMembers_And_FundInvariant
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(', 'tx2.setParticipants(List.of(p2A, p2B, p2C));\n            // Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(')
text = text.replace('tx2Id, List.of(p2A, p2B, p2C)', '// tx2Id, List.of(p2A, p2B, p2C)')
text = text.replace('List.of(tx1, tx2, tx3),\n                    participantsMap,\n                    List.of()', 'List.of(tx1, tx2, tx3), List.of(Member.builder().userId(userA).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(now.minusSeconds(100)).build()), null')

# 3. testScenario2_MixedShares
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));', 'tx.setParticipants(List.of(pA, pB, pC));')
text = text.replace('calculateBalances(List.of(tx), participantsMap, List.of())', 'calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(now.minusSeconds(100)).build()), null)')

# 4. testDistributeEvenly_WithRotation
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));', 'tx.setParticipants(List.of(pA, pB, pC));')
text = text.replace('List.of(tx), participantsMap, List.of()', 'List.of(tx), List.of(Member.builder().userId(userA).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(now.minusSeconds(100)).build()), null')

# 5. testAdjustments_DownAndUp
text = text.replace('Map<UUID, List<TransactionParticipant>> map = Map.of(\n                    txDownId, List.of(pDownA, pDownB),\n                    txUpId, List.of(pUpA, pUpB)\n            );', 'txDown.setParticipants(List.of(pDownA, pDownB));\n            txUp.setParticipants(List.of(pUpA, pUpB));')
text = text.replace('calculateBalances(List.of(txDown, txUp), map, List.of())', 'calculateBalances(List.of(txDown, txUp), List.of(Member.builder().userId(userA).joinedAt(now.minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(now.minusSeconds(100)).build()), null)')

# 6. testMembershipTimeline_OnlyActiveAtOccurredAt
text = text.replace('List<GroupMemberPeriod> periods = List.of(\n                    new GroupMemberPeriod(uActive, t0, null),\n                    new GroupMemberPeriod(uLeftLater, t0, tLeft),\n                    new GroupMemberPeriod(uLeftEarly, t0, t0.plus(1, ChronoUnit.DAYS)),\n                    new GroupMemberPeriod(uJoinedLate, tJoinedLate, null)\n            );', 'List<Member> members = List.of(\n                    Member.builder().userId(uActive).joinedAt(t0).build(),\n                    Member.builder().userId(uLeftLater).joinedAt(t0).leftAt(tLeft).build(),\n                    Member.builder().userId(uLeftEarly).joinedAt(t0).leftAt(t0.plus(1, ChronoUnit.DAYS)).build(),\n                    Member.builder().userId(uJoinedLate).joinedAt(tJoinedLate).build()\n            );')
text = text.replace('calculateBalances(List.of(tx), Map.of(), periods)', 'calculateBalances(List.of(tx), members, null)')

# 7. testFailFast_EmptyCandidatesAtOccurredAt
text = text.replace('List<GroupMemberPeriod> periods = List.of(new GroupMemberPeriod(user, tJoined, null));', 'List<Member> members = List.of(Member.builder().userId(user).joinedAt(tJoined).build());')
text = text.replace('calculateBalances(List.of(tx), Map.of(), periods)', 'calculateBalances(List.of(tx), members, null)')

# 8. testFailFast_ParticipantsSumMismatch
text = text.replace('Map<UUID, List<TransactionParticipant>> map = Map.of(txId, List.of(pA, pB));', 'tx.setParticipants(List.of(pA, pB));')
text = text.replace('calculateBalances(List.of(tx), map, List.of())', 'calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build()), null)')

# 9. testFailFast_MixedShareSumExceedsAmount
text = text.replace('Map<UUID, List<TransactionParticipant>> map = Map.of(txId, List.of(pA, pB));', 'tx.setParticipants(List.of(pA, pB));')
# Re-replace calculateBalances(List.of(tx), map, List.of()) is same as 8.
# Actually I will use regex to replace all calculateBalances\(List.of\(([^)]+)\), map, List.of\(\)\)
# Wait, let's just do it case by case.

with open('src/test/java/com/datn/financeapp/group/helper/BalanceCalculatorTest.java', 'w', encoding='utf-8') as f:
    f.write(text)
