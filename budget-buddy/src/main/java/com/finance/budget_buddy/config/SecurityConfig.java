package com.finance.budget_buddy.config;

import com.finance.budget_buddy.security.BudgetBuddyUserDetailsService;
import com.finance.budget_buddy.security.RestSecurityErrorHandler;
import com.finance.budget_buddy.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   RestSecurityErrorHandler securityErrorHandler,
                                                   JwtService tokens, Environment environment, com.fasterxml.jackson.databind.ObjectMapper json,
                                                   @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) throws Exception {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        var origins = Arrays.stream(frontendOrigin.split(",")).map(String::strip).toList();
        for (String origin : origins) {
            java.net.URI uri = java.net.URI.create(origin);
            if (origin.contains("*") || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty()
                    || !("https".equals(uri.getScheme()) || (!production && "http".equals(uri.getScheme())))) {
                throw new IllegalArgumentException("FRONTEND_ORIGIN must be explicit origins without trailing slashes (HTTPS in prod)");
            }
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        var corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/api/**", cors);
        http
                .addFilterBefore(new com.finance.budget_buddy.security.AuthThrottleFilter(json),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .cors(config -> config.configurationSource(corsSource))
                .csrf(csrf -> {
                    if (production) csrf.disable();
                    else csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .ignoringRequestMatchers("/api/web-auth/login", "/api/web-auth/register")
                            .ignoringRequestMatchers(request -> request.getHeader("Authorization") != null
                                    && request.getHeader("Authorization").startsWith("Bearer "));
                })
                .sessionManagement(session -> session.sessionCreationPolicy(production
                        ? SessionCreationPolicy.STATELESS : SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(production
                        ? new NullSecurityContextRepository() : securityContextRepository))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(tokens::authentication))
                        .authenticationEntryPoint(securityErrorHandler).accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(auth -> {
                    if (production) auth.requestMatchers("/api/auth/**", "/api/demo/**").denyAll();
                    auth.requestMatchers("/api/web-auth/register", "/api/web-auth/login", "/api/health").permitAll()
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.svg",
                                "/api/auth/csrf", "/api/auth/login", "/error",
                                "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().permitAll();
                })
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(BudgetBuddyUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
