package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.auth.LoginRequest;
import com.finance.budget_buddy.dto.auth.RegisterRequest;
import com.finance.budget_buddy.dto.auth.TokenResponse;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.security.JwtService;
import com.finance.budget_buddy.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/web-auth")
@RequiredArgsConstructor
public class WebAuthController {
    private final RegistrationService registration;
    private final AuthenticationManager authentication;
    private final JwtService tokens;

    @GetMapping("/me")
    public com.finance.budget_buddy.dto.auth.AuthSessionResponse me(@AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        return new com.finance.budget_buddy.dto.auth.AuthSessionResponse(principal.userId(), principal.email());
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user = registration.register(request);
        return ResponseEntity.status(201).body(tokens.issue(user.getUserId()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        var result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                request.email().strip().toLowerCase(Locale.ROOT), request.password()));
        return tokens.issue(((BudgetBuddyPrincipal) result.getPrincipal()).userId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        registration.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
