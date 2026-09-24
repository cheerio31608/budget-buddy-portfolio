package com.finance.budget_buddy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded, per-instance authentication throttle. Forwarded headers are not trusted here. */
public class AuthThrottleFilter extends OncePerRequestFilter {
    private final Map<String, Window> attempts = new HashMap<>();
    private final ObjectMapper json;
    public AuthThrottleFilter(ObjectMapper json) { this.json = json; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean auth = "POST".equals(request.getMethod()) && (path.equals("/api/web-auth/login")
                || path.equals("/api/web-auth/register") || path.equals("/api/auth/login"));
        if (auth && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            json.writeValue(response.getOutputStream(), ErrorResponse.of(ErrorCode.REQUEST_LIMIT_EXCEEDED));
            return;
        }
        chain.doFilter(request, response);
    }
    private synchronized boolean allow(String address) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().start >= 60_000);
        if (!attempts.containsKey(address) && attempts.size() >= 10_000) return false;
        Window window = attempts.computeIfAbsent(address, ignored -> new Window(now));
        return ++window.count <= 30;
    }
    private static class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }
}
