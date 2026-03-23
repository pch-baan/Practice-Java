package com.practice.user.application.usecase;

import com.practice.user.application.port.in.IActivateUserUseCase;
import com.practice.user.domain.exception.UserNotFoundException;
import com.practice.user.domain.model.User;
import com.practice.user.domain.port.out.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ActivateUserUseCaseImpl implements IActivateUserUseCase {

    private final IUserRepository userRepository;

    @Override
    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.activate();
        userRepository.save(user);
    }
}
