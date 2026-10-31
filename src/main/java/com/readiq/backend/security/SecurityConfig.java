package com.readiq.backend.security;

import com.readiq.backend.auth.AuthService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      AuthService auth,
      @Value("${cors.allowed-origins}") String origins,
      @Value("${security.trusted-proxy-ips:}") String proxies,
      DatabaseRateLimitStore limits)
      throws Exception {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    http.cors(c -> c.configurationSource(source))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/", "/health", "/health/live", "/health/ready")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/google",
                        "/api/auth/refresh",
                        "/api/auth/forgot-password")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/books",
                        "/api/books/*",
                        "/api/books/*/cover",
                        "/api/tips",
                        "/api/tips/*",
                        "/api/vocabulary",
                        "/api/vocabulary/*",
                        "/api/grammar/units",
                        "/api/grammar/units/*",
                        "/api/grammar/units/*/lessons",
                        "/api/grammar/lessons/*",
                        "/api/cbse/**",
                        "/api/quizzes/*",
                        "/api/quizzes/*/questions",
                        "/api/challenges/*",
                        "/api/challenges/*/leaderboard")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/razorpay/webhook")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, r, x) -> {
                          r.setStatus(401);
                          r.setContentType("application/json");
                          r.getWriter().write("{\"error\":\"Authentication required\"}");
                        })
                    .accessDeniedHandler(
                        (q, r, x) -> {
                          r.setStatus(403);
                          r.setContentType("application/json");
                          r.getWriter().write("{\"error\":\"Forbidden\"}");
                        }))
        .addFilterBefore(
            new RateLimitFilter(proxies, limits), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new RequestSizeFilter(), RateLimitFilter.class)
        .addFilterAfter(new AuthenticationFilter(auth), RateLimitFilter.class);
    return http.build();
  }
}
