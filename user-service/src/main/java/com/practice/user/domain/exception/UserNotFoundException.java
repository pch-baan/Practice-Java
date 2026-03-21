package com.practice.user.domain.exception;

public class UserNotFoundException extends UserDomainException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
