package com.seapullkb.library.service;

import com.seapullkb.library.entity.UserAccount;
import com.seapullkb.library.mapper.UserMapper;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AdminAccess {
    private final AccountService accounts;
    private final UserMapper users;
    public AdminAccess(AccountService accounts, UserMapper users) { this.accounts = accounts; this.users = users; }
    public UserAccount require(String username, boolean superOnly) {
        return validate(accounts.findByUsername(username), superOnly);
    }
    // All user locks precede resource locks; multiple users are always locked by ascending ID.
    public UserAccount lockActorAndTarget(String username, Long targetId, boolean superOnly) {
        UserAccount reference = accounts.findByUsername(username);
        if (reference == null) throw new AccessDeniedException("管理员账号不可用。");
        List<UserAccount> locked = users.lockPair(reference.getId(), targetId);
        UserAccount actor = locked.stream().filter(u -> u.getId().equals(reference.getId())).findFirst().orElse(null);
        return validate(actor, superOnly);
    }
    private UserAccount validate(UserAccount user, boolean superOnly) {
        if (user == null || user.getStatus() != 1 || !(user.getRole().equals("SUPER_ADMIN")
                || (!superOnly && user.getRole().equals("LIBRARIAN"))))
            throw new AccessDeniedException("当前账号无此管理权限，请重新登录。");
        return user;
    }
}
