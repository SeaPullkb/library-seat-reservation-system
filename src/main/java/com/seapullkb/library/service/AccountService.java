package com.seapullkb.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seapullkb.library.entity.Role;
import com.seapullkb.library.entity.UserAccount;
import com.seapullkb.library.mapper.UserMapper;
import com.seapullkb.library.web.RegistrationForm;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountService(UserMapper users, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
    }

    public UserAccount findByUsername(String username) {
        return users.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    @Transactional
    public void registerStudent(RegistrationForm form) {
        UserAccount user = new UserAccount();
        user.setUsername(form.username());
        user.setPassword(passwords.encode(form.password()));
        user.setRealName(form.realName().strip());
        user.setClassName(form.className().strip());
        user.setPhone(form.phone());
        user.setRole(Role.STUDENT.name());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now(clock));
        try {
            users.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new UsernameTakenException();
        }
    }

    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException() { super("该账号已被注册"); }
    }
}
