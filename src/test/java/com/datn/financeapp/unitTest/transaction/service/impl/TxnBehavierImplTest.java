package com.datn.financeapp.unitTest.transaction.service.impl;

import com.datn.financeapp.module.transaction.dto.response.TxnDetailRes;
import com.datn.financeapp.module.transaction.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateReq;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.events.TxnCreateEvent;
import com.datn.financeapp.module.transaction.events.TxnDeleteEvent;
import com.datn.financeapp.module.transaction.events.TxnUpdateEvent;
import com.datn.financeapp.module.transaction.mapper.TxnMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TxnBehavierImplTest {

    @Mock
    private TransactionRepository txnRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TxnMapper txnMapper;

    @Mock
    private ApplicationEventPublisher publisher;

    private TxnBehavierImpl txnBehavierService;
    private MockedStatic<SecurityContextUtil> mockedSecurityContext;
    private final UUID currentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockedSecurityContext = mockStatic(SecurityContextUtil.class);
        mockedSecurityContext.when(SecurityContextUtil::currentUserId).thenReturn(currentUserId);

        txnBehavierService = new TxnBehavierImpl(
                txnRepository, walletService, categoryService, txnMapper, publisher);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityContext.close();
    }

    @Test
    @DisplayName("create: danh mục không phải lá hoặc không tồn tại ném VALIDATION_ERROR")
    void create_invalidCategory_throwsException() {
        UUID walletId = UUID.randomUUID();
        TxnCreateReq req = new TxnCreateReq(
                TxnType.EXPENSE, 50_000L, walletId, "parent_cat", Instant.now(), "Ăn trưa", null);

        when(categoryService.findByCodeAndIsDeletedAndIsLeaf("parent_cat", false, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> txnBehavierService.create(currentUserId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());

        verify(txnRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: ví không tồn tại hoặc không thuộc user ném VALIDATION_ERROR")
    void create_invalidWallet_throwsException() {
        UUID walletId = UUID.randomUUID();
        TxnCreateReq req = new TxnCreateReq(
                TxnType.EXPENSE, 50_000L, walletId, "ca_phe", Instant.now(), "Cà phê", null);

        CateSumaryRes cateSummary = new CateSumaryRes(UUID.randomUUID(), "Cà phê", "ca_phe", "M1 1", "#112233");
        when(categoryService.findByCodeAndIsDeletedAndIsLeaf("ca_phe", false, true))
                .thenReturn(Optional.of(cateSummary));
        when(walletService.findSumaryByIdAndUser(walletId, currentUserId, false))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> txnBehavierService.create(currentUserId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());

        verify(txnRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: hợp lệ tạo thành công, phát TxnCreateEvent và trả TxnDetailResponse")
    void create_valid_success() {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();
        TxnCreateReq req = new TxnCreateReq(
                TxnType.EXPENSE, 50_000L, walletId, "ca_phe", now, "Cà phê", "Uống cùng bạn");

        CateSumaryRes cateSummary = new CateSumaryRes(UUID.randomUUID(), "Cà phê", "ca_phe", "M1 1", "#112233");
        WalletSumaryRes walletSummary = new WalletSumaryRes(walletId, "Ví chính", "icon", "#color");

        when(categoryService.findByCodeAndIsDeletedAndIsLeaf("ca_phe", false, true))
                .thenReturn(Optional.of(cateSummary));
        when(walletService.findSumaryByIdAndUser(walletId, currentUserId, false))
                .thenReturn(Optional.of(walletSummary));

        Transaction txnEntity = Transaction.builder()
                .type(TxnType.EXPENSE)
                .amount(50_000L)
                .walletId(walletId)
                .categoryCode("ca_phe")
                .occurredAt(now)
                .name("Cà phê")
                .note("Uống cùng bạn")
                .build();
        when(txnMapper.toEntity(req)).thenReturn(txnEntity);

        TxnDetailRes expectedRes = new TxnDetailRes(
                UUID.randomUUID(), walletSummary, cateSummary, TxnType.EXPENSE, 50_000L, now, "Cà phê", "Uống cùng bạn", now, now);
        when(txnMapper.toResponse(eq(txnEntity), eq(cateSummary), eq(walletSummary)))
                .thenReturn(expectedRes);

        TxnDetailRes result = txnBehavierService.create(currentUserId, req);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(txnRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(currentUserId);
        assertThat(saved.getAmount()).isEqualTo(50_000L);

        verify(publisher).publishEvent(any(TxnCreateEvent.class));
        assertThat(result).isEqualTo(expectedRes);
    }

    @Test
    @DisplayName("get: không tìm thấy hoặc đã xoá mềm (user thường) ném NOT_FOUND")
    void get_notFoundOrDeleted_throwsException() {
        UUID txnId = UUID.randomUUID();
        when(txnRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> txnBehavierService.get(txnId, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        Transaction deletedTxn = Transaction.builder()
                .id(txnId)
                .userId(currentUserId)
                .build();
        deletedTxn.setIsDeleted(true);
        when(txnRepository.findById(txnId)).thenReturn(Optional.of(deletedTxn));

        assertThatThrownBy(() -> txnBehavierService.get(txnId, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("get: giao dịch của user khác (user thường) ném NOT_FOUND")
    void get_otherUser_throwsException() {
        UUID txnId = UUID.randomUUID();
        Transaction otherUserTxn = Transaction.builder()
                .id(txnId)
                .userId(UUID.randomUUID())
                .build();
        otherUserTxn.setIsDeleted(false);
        when(txnRepository.findById(txnId)).thenReturn(Optional.of(otherUserTxn));

        assertThatThrownBy(() -> txnBehavierService.get(txnId, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("update: cập nhật số tiền và danh mục thành công, phát TxnUpdateEvent")
    void update_valid_success() {
        UUID txnId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Transaction existing = Transaction.builder()
                .id(txnId)
                .userId(currentUserId)
                .walletId(walletId)
                .categoryCode("an_uong")
                .type(TxnType.EXPENSE)
                .amount(50_000L)
                .occurredAt(Instant.now())
                .build();
        existing.setIsDeleted(false);

        when(txnRepository.findByIdAndIsDeletedFalse(txnId)).thenReturn(Optional.of(existing));

        CateSumaryRes newCate = new CateSumaryRes(UUID.randomUUID(), "Cà phê", "ca_phe", "M1 1", "#112233");
        when(categoryService.findByCodeAndIsDeletedAndIsLeaf("ca_phe", false, true))
                .thenReturn(Optional.of(newCate));

        TxnUpdateReq req = new TxnUpdateReq(null, 70_000L, null, "ca_phe", null, null, null);

        txnBehavierService.update(txnId, req, false);

        assertThat(existing.getAmount()).isEqualTo(70_000L);
        assertThat(existing.getCategoryCode()).isEqualTo("ca_phe");
        verify(txnRepository).save(existing);
        verify(publisher).publishEvent(any(TxnUpdateEvent.class));
    }

    @Test
    @DisplayName("softDelete: xoá mềm giao dịch thành công, phát TxnDeleteEvent")
    void softDelete_valid_success() {
        UUID txnId = UUID.randomUUID();
        Transaction existing = Transaction.builder()
                .id(txnId)
                .userId(currentUserId)
                .amount(50_000L)
                .build();
        existing.setIsDeleted(false);

        when(txnRepository.findByIdAndIsDeletedFalse(txnId)).thenReturn(Optional.of(existing));

        txnBehavierService.softDelete(txnId, false);

        assertThat(existing.getIsDeleted()).isTrue();
        verify(txnRepository).save(existing);
        verify(publisher).publishEvent(any(TxnDeleteEvent.class));
    }
}
