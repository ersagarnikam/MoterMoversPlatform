package uk.co.motormovers.platform.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.notification.model.PushToken;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdIn(Collection<Long> userIds);

    void deleteByTokenIn(Collection<String> tokens);

    void deleteByTokenAndUserId(String token, Long userId);
}
