package com.learnai.security;

import com.learnai.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
  private final SecretKey key;
  private final long accessMinutes;

  public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.access-minutes}") long accessMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessMinutes = accessMinutes;
  }

  public String generate(User u) {
    long now = System.currentTimeMillis();
    return Jwts.builder().subject(u.getEmail()).claim("role", u.getRole().name())
        .issuedAt(new Date(now)).expiration(new Date(now + accessMinutes * 60_000))
        .signWith(key).compact();
  }

  /** @throws io.jsonwebtoken.JwtException when invalid or expired */
  public String subject(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
  }
}
