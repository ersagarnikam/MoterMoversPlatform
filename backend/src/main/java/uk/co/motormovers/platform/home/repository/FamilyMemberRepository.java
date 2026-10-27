package uk.co.motormovers.platform.home.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.FamilyMember;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, FamilyMember.Key> {

    Optional<FamilyMember> findByUserId(Long userId);

    boolean existsByFamilyIdAndUserId(Long familyId, Long userId);

    List<FamilyMember> findByFamilyIdOrderByJoinedAt(Long familyId);
}
