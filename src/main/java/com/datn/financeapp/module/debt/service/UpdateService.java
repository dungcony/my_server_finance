package com.datn.financeapp.module.debt.service;


import java.util.UUID;

public interface UpdateService<REQ, RES> {
    RES update(UUID id, REQ req, boolean isAdmin);
}
