package com.practice.user.application.port.in;

import java.util.List;
import java.util.UUID;

public interface IDeletePendingUsersUseCase {

    void execute(List<UUID> userIds);
}
