package com.finance.budget_buddy.security;

import com.finance.budget_buddy.dto.auth.TokenResponse;
import com.finance.budget_buddy.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

/** Uses Spring Security's JWT implementation, keeping the existing principal contract. */
@Service
public class JwtService {
    private static final String ISSUER = "budget-buddy";
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final UserRepository users;
    private final Clock clock;
    private final long lifetimeSeconds;
    private final boolean production;

    public JwtService(UserRepository users, Clock clock, Environment environment,
                      @Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.ttl-seconds:1800}") long lifetimeSeconds) {
        this.users = users;
        this.clock = clock;
        this.production = environment.acceptsProfiles(Profiles.of("prod"));
        if (lifetimeSeconds < 60 || lifetimeSeconds > 3600) throw new IllegalArgumentException("JWT TTL must be 60..3600 seconds");
        this.lifetimeSeconds = lifetimeSeconds;
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() && !production) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        }
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        var secretKey = new SecretKeySpec(key, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                jwt -> jwt.getExpiresAt() != null && jwt.getAudience().contains("budget-buddy-api")
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    }

    public JwtDecoder decoder() { return decoder; }

    public TokenResponse issue(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (production && user.isDemoAccount()) throw new BadCredentialsException("Invalid credentials");
        var expiresAt = clock.instant().plusSeconds(lifetimeSeconds);
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId.toString())
                .audience(List.of("budget-buddy-api")).issuedAt(clock.instant()).expiresAt(expiresAt)
                .claim("version", user.getTokenVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt, user.getEmail());
    }

    public UsernamePasswordAuthenticationToken authentication(Jwt jwt) {
        try {
            var user = users.findById(Long.parseLong(jwt.getSubject())).orElseThrow();
            Number version = jwt.getClaim("version");
            if (version == null || version.longValue() != user.getTokenVersion()
                    || (production && user.isDemoAccount())) throw new IllegalArgumentException();
            var principal = BudgetBuddyPrincipal.user(user.getUserId(), user.getEmail(), "");
            return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, principal.getAuthorities());
        } catch (RuntimeException exception) {
            throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException("Invalid token");
        }
    }
}
