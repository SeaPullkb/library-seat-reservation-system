package com.seapullkb.library.service;

import com.seapullkb.library.entity.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialDataService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public InitialDataService(JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock) {
        this.jdbc = jdbc; this.passwords = passwords; this.clock = clock;
    }

    @Transactional
    public void initialize(boolean demo, String initialPassword) {
        if (demo) {
            createAccount("admin", "系统管理员", Role.SUPER_ADMIN, initialPassword);
            createAccount("librarian", "图书馆管理员", Role.LIBRARIAN, initialPassword);
            createAccount("student", "演示学生", Role.STUDENT, initialPassword);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM reading_room", Long.class) == 0) {
                jdbc.update("INSERT INTO reading_room (room_name, location, description) VALUES (?, ?, ?)",
                        "第一阅览室", "图书馆二层", "安静自习区");
                Long room = jdbc.queryForObject("SELECT id FROM reading_room ORDER BY id LIMIT 1", Long.class);
                for (int i = 1; i <= 20; i++) {
                    jdbc.update("INSERT INTO seat (room_id, seat_no) VALUES (?, ?)", room, "A%02d".formatted(i));
                }
            }
        } else if (jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE role = 'SUPER_ADMIN' AND status = 1", Long.class) == 0) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username = 'admin'", Long.class) != 0) {
                throw new IllegalStateException("admin 账号已存在但不是启用的超级管理员，请由现有管理员核查数据。");
            }
            createAccount("admin", "系统管理员", Role.SUPER_ADMIN, initialPassword);
        }
    }

    private void createAccount(String username, String name, Role role, String initialPassword) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username = ?", Long.class, username) != 0) return;
        if (initialPassword == null || initialPassword.length() < 12
                || initialPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("首次启动请设置 APP_DEMO_PASSWORD（dev）或 APP_BOOTSTRAP_ADMIN_PASSWORD，至少12个字符且最多72个UTF-8字节。");
        }
        jdbc.update("INSERT INTO `user` (username,password,real_name,class_name,phone,role,status,created_at) VALUES (?,?,?,?,?,?,1,?)",
                username, passwords.encode(initialPassword), name, role == Role.STUDENT ? "演示班级" : null,
                "13800000000", role.name(), LocalDateTime.now(clock));
    }
}
