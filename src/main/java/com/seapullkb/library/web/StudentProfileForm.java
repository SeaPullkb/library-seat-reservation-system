package com.seapullkb.library.web;

import jakarta.validation.constraints.*;

public record StudentProfileForm(
    @NotBlank(message="请输入姓名") @Size(max=50,message="姓名不能超过50个字符") String realName,
    @NotBlank(message="请输入班级") @Size(max=100,message="班级不能超过100个字符") String className,
    @NotBlank(message="请输入手机号") @Pattern(regexp="1[3-9][0-9]{9}",message="请输入有效的11位手机号") String phone) { }
