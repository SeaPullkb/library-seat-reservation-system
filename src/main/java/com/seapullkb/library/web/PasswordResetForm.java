package com.seapullkb.library.web;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record PasswordResetForm(@NotBlank @Size(min=12,max=64,message="新密码须为12–64个字符") String password,
    @NotBlank String confirmPassword) {
    @AssertTrue(message="两次输入的密码不一致")
    public boolean isConfirmed() { return password==null || password.equals(confirmPassword); }
    @AssertTrue(message="密码的UTF-8编码长度不能超过72字节")
    public boolean isPasswordWithinByteLimit() { return password==null || password.getBytes(StandardCharsets.UTF_8).length<=72; }
}
