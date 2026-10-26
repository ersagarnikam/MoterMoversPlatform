package uk.co.motormovers.platform.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;

public interface MilestoneAchievementRepository extends JpaRepository<MilestoneAchievement, Long> {

    boolean existsByClientId(String clientId);

    Optional<MilestoneAchievement> findByChildIdAndMilestoneId(Long childId, Long milestoneId);

    List<MilestoneAchievement> findByChildId(Long childId);

    List<MilestoneAchievement> findByChildIdInAndSchoolYear(Collection<Long> childIds, int schoolYear);
}
