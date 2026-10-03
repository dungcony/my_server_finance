package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.response.LoginRes;
import com.datn.financeapp.auth.helper.ClientInfo;

public interface LoginService<T> {

    LoginRes login(T request, ClientInfo client);
}
