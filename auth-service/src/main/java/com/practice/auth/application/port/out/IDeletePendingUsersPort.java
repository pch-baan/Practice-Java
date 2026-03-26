package com.practice.auth.application.port.out;

import java.util.List;
import java.util.UUID;

public interface IDeletePendingUsersPort {

    void deleteAllPending(List<UUID> userIds);
}
