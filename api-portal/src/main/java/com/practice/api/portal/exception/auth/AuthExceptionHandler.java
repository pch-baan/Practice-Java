package com.practice.api.portal.exception.auth;

import com.practice.api.portal.exception.ErrorResponse;
import com.practice.auth.domain.exception.AuthDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthDomainException.class)
    public ResponseEntity<ErrorResponse> handleAuthDomain(AuthDomainException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }
}
