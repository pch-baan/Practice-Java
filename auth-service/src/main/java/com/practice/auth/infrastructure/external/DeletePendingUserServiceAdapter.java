package com.practice.auth.infrastructure.external;

import com.practice.auth.application.port.out.IDeletePendingUsersPort;
import com.practice.user.application.port.in.IDeletePendingUsersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class DeletePendingUserServiceAdapter implements IDeletePendingUsersPort {

    private final IDeletePendingUsersUseCase deletePendingUsersUseCase;

    @Override
    public void deleteAllPending(List<UUID> userIds) {
        deletePendingUsersUseCase.execute(userIds);
    }
}
