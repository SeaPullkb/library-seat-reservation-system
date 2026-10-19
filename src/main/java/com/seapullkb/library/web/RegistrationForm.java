package com.seapullkb.library.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegistrationForm(
        @NotBlank(message = "请输入账号")
        @Pattern(regexp = "[A-Za-z0-9_]{4,50}", message = "账号须为4–50位字母、数字或下划线") String username,
        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 64, message = "密码须为8–64个字符") String password,
        @NotBlank(message = "请确认密码") String confirmPassword,
        @NotBlank(message = "请输入姓名") @Size(max = 50, message = "姓名不能超过50个字符") String realName,
        @NotBlank(message = "请输入班级") @Size(max = 100, message = "班级不能超过100个字符") String className,
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "1[3-9][0-9]{9}", message = "请输入有效的11位手机号") String phone) {
    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }

    @AssertTrue(message = "密码的UTF-8编码长度不能超过72字节")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
