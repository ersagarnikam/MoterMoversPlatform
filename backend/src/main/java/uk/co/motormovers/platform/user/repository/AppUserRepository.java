package uk.co.motormovers.platform.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<AppUser> findBySchoolIdOrderByDisplayName(Long schoolId);

    List<AppUser> findByTrustIdAndRoleOrderByDisplayName(Long trustId, Role role);
}
