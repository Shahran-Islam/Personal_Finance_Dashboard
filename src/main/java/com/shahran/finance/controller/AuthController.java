package com.shahran.finance.controller;

import com.shahran.finance.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final CurrentUser currentUser;

  public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, CurrentUser currentUser) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.currentUser = currentUser;
  }

  public record Registration(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 12, max = 72) String password) {}

  public record Profile(long id, String name, String email) {}

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/api/auth/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/api/auth/me")
  public Profile me() {
    return jdbc.queryForObject(
        "SELECT id,name,email FROM users WHERE id=?",
        (rs, n) -> new Profile(rs.getLong("id"), rs.getString("name"), rs.getString("email")),
        currentUser.id());
  }

  @PostMapping("/api/auth/register")
  public ResponseEntity<Void> register(@Valid @RequestBody Registration input) {
    if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
    try {
      jdbc.update(
          "INSERT INTO users(name,email,password_hash) VALUES(?,?,?)",
          input.name().strip(),
          input.email().strip().toLowerCase(Locale.ROOT),
          encoder.encode(input.password()));
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Unable to register this email. Try signing in.");
    }
    return ResponseEntity.status(201).build();
  }
}
