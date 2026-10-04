package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.response.AuthRes;
import com.datn.financeapp.auth.helper.ClientInfo;

public interface LoginService<T> {

    AuthRes login(T request, ClientInfo client);
}
