package uk.co.motormovers.platform.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.user.model.PasswordToken;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, Long> {

    Optional<PasswordToken> findByTokenHash(String tokenHash);

    List<PasswordToken> findByUserIdAndUsedAtIsNull(Long userId);
}
