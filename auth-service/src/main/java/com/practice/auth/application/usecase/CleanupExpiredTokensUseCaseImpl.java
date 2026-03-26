package com.practice.auth.application.usecase;

import com.practice.auth.application.port.in.ICleanupExpiredTokensUseCase;
import com.practice.auth.application.port.out.IDeletePendingUsersPort;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class CleanupExpiredTokensUseCaseImpl implements ICleanupExpiredTokensUseCase {

    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final IDeletePendingUsersPort deletePendingUsersPort;

    @Override
    public void execute() {
        LocalDateTime now = LocalDateTime.now();

        // ① lấy userId từ token hết hạn — phải trước khi xóa token
        List<UUID> pendingUserIds = emailVerificationTokenRepository.findUserIdsByExpiredBefore(now);

        // ② xóa PENDING users có token hết hạn (không verify)
        if (!pendingUserIds.isEmpty()) {
            deletePendingUsersPort.deleteAllPending(pendingUserIds);
        }

        // ③ xóa token hết hạn
        emailVerificationTokenRepository.deleteAllExpiredBefore(now);
    }
}
