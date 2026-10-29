package com.shahran.finance.security;

import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetailsService(JdbcTemplate jdbc) {
    return email ->
        jdbc
            .query(
                "SELECT email,password_hash FROM users WHERE email=?",
                (rs, n) ->
                    User.withUsername(rs.getString("email"))
                        .password(rs.getString("password_hash"))
                        .roles("USER")
                        .build(),
                email.strip().toLowerCase(Locale.ROOT))
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/app.css",
                        "/app.js",
                        "/favicon.svg",
                        "/api/auth/csrf",
                        "/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            login ->
                login
                    .loginProcessingUrl("/api/auth/login")
                    .usernameParameter("email")
                    .successHandler((req, res, auth) -> res.setStatus(204))
                    .failureHandler(
                        (req, res, ex) -> problem(res, 401, "Invalid email or password")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (req, res, ex) -> problem(res, 401, "Please sign in to continue"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            problem(
                                res, 403, "Your session token expired. Refresh and try again.")))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'"
                                + " 'unsafe-inline'; img-src 'self' data:; connect-src 'self';"
                                + " frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
        .addFilterBefore(new AuthRateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  static void problem(jakarta.servlet.http.HttpServletResponse response, int status, String detail)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
  }
}
