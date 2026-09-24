package com.finance.budget_buddy.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record BudgetBuddyPrincipal(
        Long userId,
        String email,
        String password,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static BudgetBuddyPrincipal user(Long userId, String email, String password) {
        return new BudgetBuddyPrincipal(userId, email, password, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
