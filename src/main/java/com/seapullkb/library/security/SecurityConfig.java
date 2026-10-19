package com.seapullkb.library.security;

import com.seapullkb.library.entity.UserAccount;
import com.seapullkb.library.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public UserDetailsService userDetailsService(AccountService accounts) {
        return username -> {
            UserAccount account = accounts.findByUsername(username);
            if (account == null) throw new UsernameNotFoundException("账号或密码错误");
            return User.withUsername(account.getUsername()).password(account.getPassword())
                    .roles(account.getRole()).disabled(account.getStatus() != 1).build();
        };
    }

    @Bean
    public SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }
    @Bean
    public HttpSessionEventPublisher sessionEvents() { return new HttpSessionEventPublisher(); }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts, SessionRegistry sessions) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/webjars/**", "/error", "/forbidden").permitAll()
                .requestMatchers("/admin/users", "/admin/users/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin", "/admin/**").hasAnyRole("LIBRARIAN", "SUPER_ADMIN")
                .requestMatchers("/student", "/student/**").hasRole("STUDENT")
                .anyRequest().authenticated())
            .addFilterAfter(new CurrentAccountFilter(accounts), AnonymousAuthenticationFilter.class)
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions).expiredUrl("/login?changed"))
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true)
                    .failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
            .exceptionHandling(ex -> ex.accessDeniedPage("/forbidden"));
        return http.build();
    }
}
