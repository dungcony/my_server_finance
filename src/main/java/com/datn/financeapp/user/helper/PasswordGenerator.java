package com.datn.financeapp.user.helper;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu ngẫu nhiên cho tài khoản Google chưa có mật khẩu.
 */
@Component
public class PasswordGenerator {

    private static final int LENGTH = 12;

    // bỏ ký tự dễ nhầm khi đọc từ email: 0 O 1 l I
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = UPPER + LOWER + DIGITS;

    private final SecureRandom random = new SecureRandom();

    /** Sinh mật khẩu mới: đủ chữ hoa, chữ thường, số và không có ký tự dễ nhầm. */
    public String generate() {
        char[] chars = new char[LENGTH];
        // mỗi nhóm một ký tự để chắc chắn đủ điều kiện, phần còn lại lấy từ cả ba nhóm
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        for (int i = 3; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        shuffle(chars);
        return new String(chars);
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    // trộn để ba ký tự bắt buộc không luôn đứng đầu
    private void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}
