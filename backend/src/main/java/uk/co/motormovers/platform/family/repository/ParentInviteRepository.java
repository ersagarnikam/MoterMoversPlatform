package uk.co.motormovers.platform.family.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.family.model.ParentInvite;

public interface ParentInviteRepository extends JpaRepository<ParentInvite, Long> {

    Optional<ParentInvite> findByCode(String code);

    boolean existsByCode(String code);
}
