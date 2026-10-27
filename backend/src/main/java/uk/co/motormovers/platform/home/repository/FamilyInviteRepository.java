package uk.co.motormovers.platform.home.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.FamilyInvite;

public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, Long> {

    Optional<FamilyInvite> findByCode(String code);
}
