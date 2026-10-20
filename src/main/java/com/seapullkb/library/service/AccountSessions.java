package com.seapullkb.library.service;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AccountSessions {
    private final SessionRegistry sessions;
    public AccountSessions(SessionRegistry sessions) { this.sessions=sessions; }
    // Call after the database transaction has committed.
    public void expire(String username) {
        for(Object principal:sessions.getAllPrincipals()) {
            if(principal instanceof UserDetails details && details.getUsername().equalsIgnoreCase(username))
                sessions.getAllSessions(principal,false).forEach(session -> session.expireNow());
        }
    }
}
