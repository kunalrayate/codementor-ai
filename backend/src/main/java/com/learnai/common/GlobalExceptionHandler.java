package com.learnai.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, String>> api(ApiException e) {
    return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
        .map(f -> f.getField() + " " + f.getDefaultMessage()).orElse("Invalid request");
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> other(Exception e) {
    log.error("Unhandled error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
  }
}
