package com.finance.budget_buddy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.assertThat;

class AuthThrottleFilterTest {
    @Test void thirtyFirstAuthenticationAttemptIsLimitedWithoutEchoingInput() throws Exception {
        var filter = new AuthThrottleFilter(new ObjectMapper());
        for (int i = 0; i < 31; i++) {
            var request = new MockHttpServletRequest("POST", "/api/web-auth/login");
            request.setServletPath("/api/web-auth/login");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(i < 30 ? 200 : 429);
            if (i == 30) assertThat(response.getContentAsString()).contains("LIMIT001");
        }
    }
}
