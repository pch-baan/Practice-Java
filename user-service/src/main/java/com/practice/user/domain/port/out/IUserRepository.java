package com.practice.user.domain.port.out;

import com.practice.user.domain.model.User;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    boolean existsByEmail(EmailVO email);

    boolean existsByUsername(UsernameVO username);

    Optional<User> findByUsername(UsernameVO username);

    Optional<User> findByEmail(EmailVO email);

    void deleteAllPendingByIds(List<UUID> userIds);
}
