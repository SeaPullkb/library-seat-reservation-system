package com.seapullkb.library.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@ControllerAdvice
public class PageAdvice {
    private final Environment environment;
    private final Clock clock;
    public PageAdvice(Environment environment,Clock clock) { this.environment=environment;this.clock=clock; }
    @ModelAttribute("demoEnvironment")
    public boolean demoEnvironment() { return environment.acceptsProfiles(Profiles.of("demo")); }
    @ModelAttribute("businessNow")
    public LocalDateTime businessNow() { return LocalDateTime.now(clock); }
    private boolean role(Authentication authentication,String name) {
        return signedIn(authentication) && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_"+name));
    }
    @ModelAttribute("studentUser")
    public boolean student(Authentication authentication) { return role(authentication,"STUDENT"); }
    @ModelAttribute("adminUser")
    public boolean admin(Authentication authentication) { return role(authentication,"LIBRARIAN") || role(authentication,"SUPER_ADMIN"); }
    @ModelAttribute("superAdminUser")
    public boolean superAdmin(Authentication authentication) { return role(authentication,"SUPER_ADMIN"); }
    @ModelAttribute("signedIn")
    public boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
