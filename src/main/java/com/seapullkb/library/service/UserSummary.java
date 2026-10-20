package com.seapullkb.library.service;

import java.time.LocalDateTime;

public record UserSummary(Long id,String username,String realName,String className,String phone,String role,Integer status,LocalDateTime createdAt) {
    public String getRoleLabel() { return switch(role) { case "SUPER_ADMIN" -> "超级管理员"; case "LIBRARIAN" -> "图书馆管理员"; default -> "学生"; }; }
}
