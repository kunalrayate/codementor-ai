package com.learnai.web;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService auth;
  @Value("${app.version}") private String version;
  /** The frontend compares this with its own version to warn when backend and frontend come from different folders. */
  @GetMapping("/version") public Map<String, String> version() { return Map.of("version", version); }
  @PostMapping("/register") public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }
  @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
  @PostMapping("/refresh") public AuthResponse refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }
  @PostMapping("/logout") public void logout(@Valid @RequestBody RefreshRequest r) { auth.logout(r.refreshToken()); }
}
