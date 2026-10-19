package com.seapullkb.library.web;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record StudentPasswordForm(@NotBlank(message="请输入当前密码") @Size(max=64) String currentPassword,
    @NotBlank(message="请输入新密码") @Size(min=8,max=64,message="新密码须为8–64个字符") String password,
    @NotBlank(message="请确认新密码") String confirmPassword) {
    @AssertTrue(message="两次输入的新密码不一致")
    public boolean isConfirmed() { return password==null || password.equals(confirmPassword); }
    @AssertTrue(message="密码的UTF-8编码长度不能超过72字节")
    public boolean isWithinByteLimit() {
        return (password==null || password.getBytes(StandardCharsets.UTF_8).length<=72)
            && (currentPassword==null || currentPassword.getBytes(StandardCharsets.UTF_8).length<=72);
    }
}
