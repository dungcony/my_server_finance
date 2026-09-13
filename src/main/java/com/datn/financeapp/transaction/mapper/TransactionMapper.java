package com.datn.financeapp.transaction.mapper;

import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.dto.response.IconRefResponse;
import com.datn.financeapp.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.transaction.dto.response.TransactionListItemResponse;
import com.datn.financeapp.transaction.dto.response.TransactionRefResponse;
import com.datn.financeapp.transaction.dto.response.TransactionResponse;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionRefResponse toRef(Transaction txn);

    GeneratedTransactionResponse toGeneratedResponse(Transaction txn);

    @Mapping(target = "id", source = "txn.id")
    @Mapping(target = "type", source = "txn.type")
    @Mapping(target = "amount", source = "txn.amount")
    @Mapping(target = "date", source = "txn.date")
    @Mapping(target = "displayName", source = "txn.displayName")
    @Mapping(target = "note", source = "txn.note")
    @Mapping(target = "source", source = "txn.source")
    @Mapping(target = "countsInReport", source = "txn.countsInReport")
    @Mapping(target = "wallet", expression = "java(toResponseWalletRef(wallet))")
    @Mapping(target = "destinationWallet", expression = "java(toResponseWalletRef(destinationWallet))")
    @Mapping(target = "category", expression = "java(toResponseCategoryRef(category))")
    @Mapping(target = "receiptUrl", source = "txn.receiptUrl")
    @Mapping(target = "recurringId", source = "txn.recurringId")
    @Mapping(target = "draftId", source = "txn.draftId")
    @Mapping(target = "createdAt", source = "txn.createdAt")
    @Mapping(target = "updatedAt", source = "txn.updatedAt")
    TransactionResponse toResponse(
            Transaction txn,
            WalletRefResponse wallet,
            WalletRefResponse destinationWallet,
            CategoryRefResponse category);

    @Mapping(target = "id", source = "txn.id")
    @Mapping(target = "type", source = "txn.type")
    @Mapping(target = "amount", source = "txn.amount")
    @Mapping(target = "date", source = "txn.date")
    @Mapping(target = "displayName", source = "txn.displayName")
    @Mapping(target = "note", source = "txn.note")
    @Mapping(target = "source", source = "txn.source")
    @Mapping(target = "countsInReport", source = "txn.countsInReport")
    @Mapping(target = "wallet", expression = "java(toListItemWalletRef(wallet))")
    @Mapping(target = "destinationWallet", expression = "java(toListItemWalletRef(destinationWallet))")
    @Mapping(target = "category", source = "categoryRef")
    @Mapping(target = "receiptUrl", source = "txn.receiptUrl")
    @Mapping(target = "recurringId", source = "txn.recurringId")
    @Mapping(target = "draftId", source = "txn.draftId")
    @Mapping(target = "createdAt", source = "txn.createdAt")
    @Mapping(target = "updatedAt", source = "txn.updatedAt")
    TransactionListItemResponse toListItemResponse(
            Transaction txn,
            WalletRefResponse wallet,
            WalletRefResponse destinationWallet,
            TransactionListItemResponse.CategoryRef categoryRef);

    default TransactionDetailResponse toDetailResponse(
            TransactionListItemResponse base,
            TransactionDetailResponse.AiDraftRef aiDrafts) {
        return new TransactionDetailResponse(
                base.id(),
                base.type(),
                base.amount(),
                base.date(),
                base.displayName(),
                base.note(),
                base.source(),
                base.countsInReport(),
                base.wallet(),
                base.destinationWallet(),
                base.category(),
                base.receiptUrl(),
                base.recurringId(),
                base.draftId(),
                base.createdAt(),
                base.updatedAt(),
                null,
                null,
                aiDrafts);
    }

    default TransactionListItemResponse.WalletRef toListItemWalletRef(WalletRefResponse wallet) {
        return wallet != null ? new TransactionListItemResponse.WalletRef(wallet.id(), wallet.name(), wallet.type()) : null;
    }

    default TransactionResponse.WalletRef toResponseWalletRef(WalletRefResponse wallet) {
        return wallet != null ? new TransactionResponse.WalletRef(wallet.id(), wallet.name(), wallet.type()) : null;
    }

    default TransactionResponse.CategoryRef toResponseCategoryRef(CategoryRefResponse category) {
        return category != null
                ? new TransactionResponse.CategoryRef(category.id(), category.name(), category.type(), category.parentCategoryId())
                : null;
    }

    default TransactionListItemResponse.CategoryRef toListItemCategoryRef(
            CategoryRefResponse category,
            CategoryRefResponse parent) {
        if (category == null) {
            return null;
        }
        IconRefResponse icon = category.icon();
        TransactionListItemResponse.IconRef iconRef =
                icon != null ? new TransactionListItemResponse.IconRef(icon.code(), icon.pathData()) : null;
        TransactionListItemResponse.ParentRef parentRef =
                parent != null ? new TransactionListItemResponse.ParentRef(parent.id(), parent.name()) : null;
        return new TransactionListItemResponse.CategoryRef(
                category.id(), category.name(), category.type(), iconRef, category.color(), parentRef);
    }
}
