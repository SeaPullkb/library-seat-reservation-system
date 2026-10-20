package com.seapullkb.library.service;

import com.seapullkb.library.mapper.UserMapper;
import com.seapullkb.library.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StudentProfileService {
    private final ReservationLocks locks;
    private final ReservationTransactions transactions;
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final AccountSessions sessions;
    public StudentProfileService(ReservationLocks locks,ReservationTransactions transactions,UserMapper users,PasswordEncoder passwords,AccountSessions sessions) {
        this.locks=locks;this.transactions=transactions;this.users=users;this.passwords=passwords;this.sessions=sessions;
    }
    public UserSummary mine(String username) {
        return transactions.execute(() -> {
            var user=locks.student(username);
            return new UserSummary(user.getId(),user.getUsername(),user.getRealName(),user.getClassName(),user.getPhone(),user.getRole(),user.getStatus(),user.getCreatedAt());
        });
    }
    public void save(String username,StudentProfileForm form) {
        transactions.execute(() -> {
            var user=locks.student(username);users.editProfile(user.getId(),form.realName().strip(),form.className().strip(),form.phone());return null;
        });
    }
    public void password(String username,StudentPasswordForm form) {
        transactions.execute(() -> {
            var user=locks.student(username);
            if(!passwords.matches(form.currentPassword(),user.getPassword())) throw new MaintenanceConflictException("当前密码不正确。");
            if(passwords.matches(form.password(),user.getPassword())) throw new MaintenanceConflictException("新密码不能与当前密码相同。");
            users.changePassword(user.getId(),passwords.encode(form.password()));return null;
        });
        sessions.expire(username);
    }
}
