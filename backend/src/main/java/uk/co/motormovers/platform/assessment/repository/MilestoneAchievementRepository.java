package uk.co.motormovers.platform.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.child.model.Child;

public interface MilestoneAchievementRepository extends JpaRepository<MilestoneAchievement, Long> {

    boolean existsByClientId(String clientId);

    Optional<MilestoneAchievement> findByChildIdAndMilestoneId(Long childId, Long milestoneId);

    List<MilestoneAchievement> findByChildId(Long childId);

    List<MilestoneAchievement> findByChildIdInAndSchoolYear(Collection<Long> childIds, int schoolYear);

    /** Moves a child's secure movements to another child, except those the other child already has. */
    @Modifying
    @Query("update MilestoneAchievement a set a.child = :to where a.child = :from and not exists "
            + "(select b.id from MilestoneAchievement b where b.child = :to and b.milestone = a.milestone)")
    int moveMissing(Child from, Child to);
}
