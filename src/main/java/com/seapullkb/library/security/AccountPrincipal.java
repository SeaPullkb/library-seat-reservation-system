package com.seapullkb.library.security;

import com.seapullkb.library.entity.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Credential erasure may clear User.password; retain only a fingerprint of its BCrypt hash. */
public class AccountPrincipal extends User {
    private static final long serialVersionUID=1L;
    private final byte[] passwordStamp;
    public AccountPrincipal(UserAccount account) {
        super(account.getUsername(),account.getPassword(),account.getStatus()==1,true,true,true,
                List.of(new SimpleGrantedAuthority("ROLE_"+account.getRole())));
        passwordStamp=stamp(account.getPassword());
    }
    public boolean matchesPasswordStamp(String encoded) { return MessageDigest.isEqual(passwordStamp,stamp(encoded)); }
    private static byte[] stamp(String encoded) {
        try { return MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }
}
