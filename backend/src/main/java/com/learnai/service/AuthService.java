package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  @Value("${app.jwt.refresh-days}") private long refreshDays;

  @Transactional
  public AuthResponse register(RegisterRequest r) {
    String email = r.email().trim().toLowerCase();
    if (users.existsByEmail(email)) throw ApiException.badRequest("Email already registered");
    User u = new User();
    u.setName(r.name().trim());
    u.setEmail(email);
    u.setPasswordHash(encoder.encode(r.password()));
    // self-registration can never create an ADMIN
    u.setRole(r.role() == Role.INSTRUCTOR ? Role.INSTRUCTOR : Role.STUDENT);
    users.save(u);
    return issue(u);
  }

  @Transactional
  public AuthResponse login(LoginRequest r) {
    User u = users.findByEmail(r.email().trim().toLowerCase())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    if (!u.isEnabled() || !encoder.matches(r.password(), u.getPasswordHash()))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    return issue(u);
  }

  @Transactional
  public AuthResponse refresh(String token) {
    RefreshToken t = tokens.findByToken(token)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    tokens.delete(t);
    if (t.getExpiresAt().isBefore(LocalDateTime.now()) || !t.getUser().isEnabled())
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
    return issue(t.getUser()); // rotation: old token is consumed
  }

  @Transactional
  public void logout(String token) { tokens.findByToken(token).ifPresent(tokens::delete); }

  private AuthResponse issue(User u) {
    RefreshToken rt = new RefreshToken();
    rt.setToken(UUID.randomUUID().toString());
    rt.setUser(u);
    rt.setExpiresAt(LocalDateTime.now().plusDays(refreshDays));
    tokens.save(rt);
    return new AuthResponse(jwt.generate(u), rt.getToken(), Mappers.info(u));
  }
}
