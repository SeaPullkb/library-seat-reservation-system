package com.seapullkb.library.security;

import com.seapullkb.library.service.AccountService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject stale roles and disabled accounts even before a session-expiration redirect is processed. */
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    public CurrentAccountFilter(AccountService accounts) { this.accounts=accounts; }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication!=null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            var account=accounts.findByUsername(authentication.getName());
            if (account==null || account.getStatus()!=1
                    || (authentication.getPrincipal() instanceof AccountPrincipal principal && !principal.matchesPasswordStamp(account.getPassword()))
                    || authentication.getAuthorities().stream()
                    .noneMatch(a -> a.getAuthority().equals("ROLE_"+account.getRole()))) {
                new SecurityContextLogoutHandler().logout(request,response,authentication);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,"账号状态或权限已变化，请重新登录。");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
