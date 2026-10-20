package com.seapullkb.library.web;

import jakarta.validation.constraints.*;

public record UserEditForm(@NotBlank @Size(max=50,message="姓名不能超过50个字符") String realName,
    @Size(max=100,message="班级不能超过100个字符") String className,
    @NotBlank @Pattern(regexp="1[3-9][0-9]{9}",message="请输入有效的11位手机号") String phone) { }
