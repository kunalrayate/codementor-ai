package com.learnai.security;

import com.learnai.repo.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UserRepository users;

  public JwtAuthFilter(JwtService jwt, UserRepository users) { this.jwt = jwt; this.users = users; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        String email = jwt.subject(header.substring(7));
        users.findByEmail(email).filter(u -> u.isEnabled()).ifPresent(u -> {
          var auth = new UsernamePasswordAuthenticationToken(u, null,
              List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
          SecurityContextHolder.getContext().setAuthentication(auth);
        });
      } catch (JwtException | IllegalArgumentException ignored) {
      }
    }
    chain.doFilter(req, res);
  }
}
