package com.datn.financeapp.module.auth.service;

import com.datn.financeapp.module.auth.dto.response.AuthRes;
import com.datn.financeapp.module.auth.helper.ClientInfo;

public interface LoginService<T> {

    AuthRes login(T request, ClientInfo client);
}
