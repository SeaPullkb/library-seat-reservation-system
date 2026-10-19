package com.seapullkb.library.service;

import com.seapullkb.library.entity.*;
import com.seapullkb.library.mapper.UserMapper;
import com.seapullkb.library.web.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private final UserMapper users;
    private final AdminAccess access;
    private final ReservationTransactions transactions;
    private final PasswordEncoder passwords;
    private final SessionRegistry sessions;
    private final Clock clock;
    public UserAdminService(UserMapper users,AdminAccess access,ReservationTransactions transactions,PasswordEncoder passwords,SessionRegistry sessions,Clock clock) {
        this.users=users; this.access=access; this.transactions=transactions; this.passwords=passwords; this.sessions=sessions; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public AdminPage<UserSummary> search(String actor,String keyword,Role role,Integer status,int page) {
        access.require(actor,true); AdminPage.requirePage(page);
        if (keyword.length()>50 || (status!=null && status!=0 && status!=1)) throw new IllegalArgumentException("查询条件无效。");
        String filter=role==null?null:role.name();
        return new AdminPage<>(users.search(keyword.strip(),filter,status,(long)page*20),users.searchCount(keyword.strip(),filter,status),page,20);
    }
    public UserSummary detail(String actor,Long id) {
        access.require(actor,true); var u=users.selectById(id);
        if (u==null) throw new ResourceNotFoundException("用户不存在。");
        return new UserSummary(u.getId(),u.getUsername(),u.getRealName(),u.getClassName(),u.getPhone(),u.getRole(),u.getStatus(),u.getCreatedAt());
    }
    // Deployment is one JVM by requirement. Serialize account administration through commit,
    // so concurrent last-super-admin demotions cannot both pass their count check.
    public synchronized Long createAdministrator(String actor,AdminCreateForm form) {
        String hash=passwords.encode(form.password());
        return transactions.execute(() -> {
            access.lockActorAndTarget(actor,-1L,true);
            UserAccount u=new UserAccount(); u.setUsername(form.username()); u.setPassword(hash); u.setRealName(form.realName().strip());
            u.setPhone(form.phone()); u.setRole(Role.LIBRARIAN.name()); u.setStatus(1); u.setCreatedAt(LocalDateTime.now(clock));
            try { users.insert(u); } catch(DuplicateKeyException ex) { throw new AccountService.UsernameTakenException(); }
            return u.getId();
        });
    }
    public synchronized void edit(String actor,Long id,UserEditForm form) {
        transactions.execute(() -> {
            var target=target(actor,id);
            users.editProfile(id,form.realName().strip(),form.className()==null?null:form.className().strip(),form.phone());
            return target;
        });
    }
    public synchronized void role(String actor,Long id,Role role) {
        var target=transactions.execute(() -> {
            var operator=access.lockActorAndTarget(actor,id,true); var user=required(id);
            if (user.getRole().equals(role.name())) return user;
            if (operator.getId().equals(id)) throw new MaintenanceConflictException("不能修改自己的角色，请由其他超级管理员操作。");
            requireNoActive(id); requireSuperAdminRemains(user,role,user.getStatus());
            users.changeRole(id,role.name()); return user;
        });
        expireSessions(target.getUsername());
    }
    public synchronized void enabled(String actor,Long id,boolean enabled) {
        var target=transactions.execute(() -> {
            var operator=access.lockActorAndTarget(actor,id,true); var user=required(id);
            if (!enabled) {
                if (operator.getId().equals(id)) throw new MaintenanceConflictException("不能禁用自己的账号。");
                requireNoActive(id); requireSuperAdminRemains(user,Role.valueOf(user.getRole()),0);
            }
            users.changeStatus(id,enabled?1:0); return user;
        });
        expireSessions(target.getUsername());
    }
    public synchronized void resetPassword(String actor,Long id,PasswordResetForm form) {
        String hash=passwords.encode(form.password());
        var target=transactions.execute(() -> { var user=target(actor,id); users.changePassword(id,hash); return user; });
        expireSessions(target.getUsername());
    }
    private UserAccount target(String actor,Long id) { access.lockActorAndTarget(actor,id,true); return required(id); }
    private UserAccount required(Long id) {
        var u=users.selectById(id); if(u==null) throw new ResourceNotFoundException("用户不存在。"); return u;
    }
    private void requireNoActive(Long id) {
        if(users.openReservationCount(id)>0) throw new MaintenanceConflictException("该用户仍有待签到或使用中预约，请先取消、释放或完成后再禁用或调整角色。");
    }
    private void requireSuperAdminRemains(UserAccount user,Role nextRole,int nextStatus) {
        if(user.getStatus()==1 && user.getRole().equals("SUPER_ADMIN") && (nextRole!=Role.SUPER_ADMIN || nextStatus!=1)
                && users.enabledSuperAdmins()<=1) throw new MaintenanceConflictException("必须保留至少一个启用的超级管理员。");
    }
    private void expireSessions(String username) {
        for(Object principal:sessions.getAllPrincipals()) {
            if(principal instanceof UserDetails details && details.getUsername().equalsIgnoreCase(username))
                sessions.getAllSessions(principal,false).forEach(session -> session.expireNow());
        }
    }
}
