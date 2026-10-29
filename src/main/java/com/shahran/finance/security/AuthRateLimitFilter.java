package com.shahran.finance.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds authentication attempts per client address for a single application instance. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Window(Instant start, int count) {}

  private final Map<String, Window> attempts = new HashMap<>();

  private synchronized boolean allowed(String address) {
    Instant now = Instant.now(), cutoff = now.minus(Duration.ofMinutes(15));
    attempts.entrySet().removeIf(entry -> entry.getValue().start().isBefore(cutoff));
    Window window = attempts.get(address);
    if (window == null) {
      if (attempts.size() >= 4096) return false;
      attempts.put(address, new Window(now, 1));
      return true;
    }
    if (window.count() >= 30) return false;
    attempts.put(address, new Window(window.start(), window.count() + 1));
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    boolean authPost =
        req.getMethod().equals("POST")
            && (req.getServletPath().equals("/api/auth/login")
                || req.getServletPath().equals("/api/auth/register"));
    if (authPost && !allowed(req.getRemoteAddr())) {
      res.setHeader("Retry-After", "900");
      SecurityConfig.problem(res, 429, "Too many sign-in attempts. Try again in 15 minutes.");
      return;
    }
    chain.doFilter(req, res);
  }
}
