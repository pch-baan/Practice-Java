package com.practice.user.application.usecase;

import com.practice.user.application.port.in.IDeletePendingUsersUseCase;
import com.practice.user.domain.port.out.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeletePendingUsersUseCaseImpl implements IDeletePendingUsersUseCase {

    private final IUserRepository userRepository;

    @Override
    public void execute(List<UUID> userIds) {
        userRepository.deleteAllPendingByIds(userIds);
    }
}
