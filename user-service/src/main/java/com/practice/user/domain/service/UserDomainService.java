package com.practice.user.domain.service;

import com.practice.user.domain.exception.UserConflictException;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;

public class UserDomainService {

    private final IUserRepository userRepository;

    public UserDomainService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void validateUniqueConstraints(EmailVO email, UsernameVO username) {
        if (userRepository.existsByEmail(email)) {
            throw new UserConflictException("Email already exists: " + email.getValue());
        }
        if (userRepository.existsByUsername(username)) {
            throw new UserConflictException("Username already exists: " + username.getValue());
        }
    }
}
