package com.finance.budget_buddy.dto.auth;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, String email) {}
