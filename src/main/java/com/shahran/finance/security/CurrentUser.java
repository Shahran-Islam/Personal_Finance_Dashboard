package com.shahran.finance.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
  private final JdbcTemplate jdbc;

  public CurrentUser(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long id() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
    return jdbc
        .query("SELECT id FROM users WHERE email=?", (rs, n) -> rs.getLong(1), auth.getName())
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
  }
}
