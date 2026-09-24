package com.finance.budget_buddy.security;

import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtServiceTest {
    @Test void expiredTokenAndWrongSignatureAreRejected() {
        var users = mock(UserRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().email("test@example.com").passwordHash("unused").build()));
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        var oldClock = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        var expired = new JwtService(users, oldClock, new MockEnvironment(), secret, 60);
        String expiredToken = expired.issue(1L).accessToken();
        assertThatThrownBy(() -> expired.decoder().decode(expiredToken)).isInstanceOf(JwtException.class);
        var issuer = new JwtService(users, Clock.systemUTC(), new MockEnvironment(), secret, 1800);
        var otherServer = new JwtService(users, Clock.systemUTC(), new MockEnvironment(), UUID.randomUUID().toString(), 1800);
        assertThatThrownBy(() -> otherServer.decoder().decode(issuer.issue(1L).accessToken())).isInstanceOf(JwtException.class);
    }
    @Test void productionDoesNotAcceptAnEmptySecret() {
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtService(mock(UserRepository.class), Clock.systemUTC(), env, "", 1800))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
