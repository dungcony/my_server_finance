package com.datn.financeapp.user.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @RepeatedTest(100)
    @DisplayName("Mật khẩu sinh ra dài 12 ký tự, có chữ hoa, chữ thường và số")
    void generate_hasRequiredLengthAndCharacterGroups() {
        String password = generator.generate();

        assertThat(password).hasSize(12);
        assertThat(password).matches(".*[A-Z].*");
        assertThat(password).matches(".*[a-z].*");
        assertThat(password).matches(".*[0-9].*");
    }

    @RepeatedTest(100)
    @DisplayName("Mật khẩu sinh ra không chứa ký tự dễ nhầm (0 O 1 l I) và chỉ gồm chữ + số")
    void generate_excludesAmbiguousCharacters() {
        String password = generator.generate();

        assertThat(password).matches("[A-Za-z0-9]+");
        assertThat(password).doesNotContainPattern("[0O1lI]");
    }

    @Test
    @DisplayName("Hai lần sinh liên tiếp cho hai mật khẩu khác nhau")
    void generate_twoCalls_returnDifferentPasswords() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
