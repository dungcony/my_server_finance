package com.datn.financeapp.module.wallet.envents;

import com.datn.financeapp.module.user.event.publiser.UserCreateEvent;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletListener {

    private final WalletService walletService;

    public void onCreateUser(UserCreateEvent event) {
        walletService.createDefaultCashWallet(event.userId());
    }
}
