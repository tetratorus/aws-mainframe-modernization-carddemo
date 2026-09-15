package com.carddemo.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ApiError> businessRule(BusinessRuleException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ApiError(e.getMessage(), e.getField()));
  }

  @ExceptionHandler(UnauthenticatedException.class)
  public ResponseEntity<ApiError> unauthenticated(UnauthenticatedException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage(), null));
  }
}
