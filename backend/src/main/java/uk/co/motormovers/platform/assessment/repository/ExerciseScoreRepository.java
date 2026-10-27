package uk.co.motormovers.platform.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.model.ColourStage;

public interface ExerciseScoreRepository extends JpaRepository<ExerciseScore, Long> {

    boolean existsByClientId(String clientId);

    Optional<ExerciseScore> findByChildIdAndExerciseIdAndPoint(Long childId, Long exerciseId, String point);

    List<ExerciseScore> findByChildIdAndExerciseStage(Long childId, ColourStage stage);

    List<ExerciseScore> findByChildIdInAndExerciseStage(Collection<Long> childIds, ColourStage stage);

    /** Moves a child's scores to another child, except where the other child already has a score at that point. */
    @Modifying
    @Query("update ExerciseScore s set s.child = :to where s.child = :from and not exists "
            + "(select t.id from ExerciseScore t where t.child = :to and t.exercise = s.exercise and t.point = s.point)")
    int moveMissing(Child from, Child to);
}
