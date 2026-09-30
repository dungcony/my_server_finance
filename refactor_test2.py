import re

with open('src/test/java/com/datn/financeapp/group/helper/BalanceCalculatorTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace setup
text = re.sub(r'private BalanceCalculator calculator;\s*@BeforeEach\s*void setUp\(\) \{\s*calculator = new BalanceCalculator\(\);\s*\}', '', text)

# Imports
text = text.replace('import com.datn.financeapp.group.entity.TransactionParticipant;', 'import com.datn.financeapp.group.entity.TransactionParticipant;\nimport com.datn.financeapp.group.entity.Member;')

# Replace Map.of(tx1Id, List.of(p1, p2), ...) with tx.setParticipants()
# Case 1: testScenario1_ThreeMembers_And_FundInvariant
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(\n                    tx2Id, List.of(p2A, p2B, p2C)\n            );', 'tx2.setParticipants(List.of(p2A, p2B, p2C));')
text = text.replace('calculator.calculateBalances(\n                    List.of(tx1, tx2, tx3),\n                    participantsMap,\n                    List.of()\n            )', 'BalanceCalculator.calculateBalances(\n                    List.of(tx1, tx2, tx3), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null\n            )')

# Case 2: testScenario2_MixedShares
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));', 'tx.setParticipants(List.of(pA, pB, pC));')
text = text.replace('calculator.calculateBalances(List.of(tx), participantsMap, List.of())', 'BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null)')

# Case 3: testDistributeEvenly_WithRotation
text = text.replace('Map<UUID, List<TransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));', 'tx.setParticipants(List.of(pA, pB, pC));')
# Already replaced by Case 2's calculateBalances replace.

# Case 4: testAdjustments_DownAndUp
text = text.replace('Map<UUID, List<TransactionParticipant>> map = Map.of(\n                    txDownId, List.of(pDownA, pDownB),\n                    txUpId, List.of(pUpA, pUpB)\n            );', 'txDown.setParticipants(List.of(pDownA, pDownB));\n            txUp.setParticipants(List.of(pUpA, pUpB));')
text = text.replace('calculator.calculateBalances(List.of(txDown, txUp), map, List.of())', 'BalanceCalculator.calculateBalances(List.of(txDown, txUp), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build()), null)')

# Case 5: testMembershipTimeline_OnlyActiveAtOccurredAt
text = text.replace('List<GroupMemberPeriod> periods = List.of(\n                    new GroupMemberPeriod(uActive, t0, null),\n                    new GroupMemberPeriod(uLeftLater, t0, tLeft),\n                    new GroupMemberPeriod(uLeftEarly, t0, t0.plus(1, ChronoUnit.DAYS)),\n                    new GroupMemberPeriod(uJoinedLate, tJoinedLate, null)\n            );', 'List<Member> members = List.of(\n                    Member.builder().userId(uActive).joinedAt(t0).build(),\n                    Member.builder().userId(uLeftLater).joinedAt(t0).leftAt(tLeft).build(),\n                    Member.builder().userId(uLeftEarly).joinedAt(t0).leftAt(t0.plus(1, ChronoUnit.DAYS)).build(),\n                    Member.builder().userId(uJoinedLate).joinedAt(tJoinedLate).build()\n            );')
text = text.replace('calculator.calculateBalances(List.of(tx), Map.of(), periods)', 'BalanceCalculator.calculateBalances(List.of(tx), members, null)')

# Case 6: testFailFast_EmptyCandidatesAtOccurredAt
text = text.replace('List<GroupMemberPeriod> periods = List.of(new GroupMemberPeriod(user, tJoined, null));', 'List<Member> members = List.of(Member.builder().userId(user).joinedAt(tJoined).build());')
text = text.replace('calculator.calculateBalances(List.of(tx), Map.of(), periods)', 'BalanceCalculator.calculateBalances(List.of(tx), members, null)')

# Generic maps
text = re.sub(r'Map<UUID, List<TransactionParticipant>> map = Map\.of\(txId, List\.of\(([^)]+)\)\);', r'tx.setParticipants(List.of(\1));', text)
text = re.sub(r'Map<UUID, List<TransactionParticipant>> map = Map\.of\(tx\.getId\(\), List\.of\(([^)]+)\)\);', r'tx.setParticipants(List.of(\1));', text)

# For testFailFast_ParticipantsSumMismatch, testFailFast_MixedShareSumExceedsAmount, testFailFast_NegativeOrZeroShareAmount, testFailFast_DuplicateParticipantUserId, testExpensePersonal_ThrowsWhenTransactorNull, testExpenseFund_AllowsTransactorNull
# They all use calculator.calculateBalances(List.of(tx), map, List.of())
text = text.replace('calculator.calculateBalances(List.of(tx), map, List.of())', 'BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB != null ? userB : UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null)')
text = text.replace('userB != null ? userB : UUID.randomUUID()', 'UUID.randomUUID()')

# Remaining generic empty periods:
text = text.replace('calculator.calculateBalances(List.of(tx), Map.of(), List.of())', 'BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA != null ? userA : UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null)')
text = text.replace('userA != null ? userA : UUID.randomUUID()', 'UUID.randomUUID()')

text = text.replace('calculator.calculateBalances(List.of(), Map.of(), List.of())', 'BalanceCalculator.calculateBalances(List.of(), List.of(), null)')

with open('src/test/java/com/datn/financeapp/group/helper/BalanceCalculatorTest.java', 'w', encoding='utf-8') as f:
    f.write(text)

