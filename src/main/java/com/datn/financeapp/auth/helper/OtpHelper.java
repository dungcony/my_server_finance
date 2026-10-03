package com.datn.financeapp.auth.helper;

import java.security.SecureRandom;

public class OtpHelper {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String create(int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }

        return sb.toString();
    }
}
