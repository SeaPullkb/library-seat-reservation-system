package com.seapullkb.library.web;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record AdminCreateForm(
    @NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,50}",message="账号须为4–50位字母、数字或下划线") String username,
    @NotBlank @Size(min=12,max=64,message="管理员初始密码须为12–64个字符") String password,
    @NotBlank @Size(max=50,message="姓名不能超过50个字符") String realName,
    @NotBlank @Pattern(regexp="1[3-9][0-9]{9}",message="请输入有效的11位手机号") String phone) {
    @AssertTrue(message="密码的UTF-8编码长度不能超过72字节")
    public boolean isPasswordWithinByteLimit() { return password==null || password.getBytes(StandardCharsets.UTF_8).length<=72; }
}
