package uk.co.motormovers.platform.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.common.model.ColourStage;

public interface ExerciseScoreRepository extends JpaRepository<ExerciseScore, Long> {

    boolean existsByClientId(String clientId);

    Optional<ExerciseScore> findByChildIdAndExerciseIdAndPoint(Long childId, Long exerciseId, String point);

    List<ExerciseScore> findByChildIdAndExerciseStage(Long childId, ColourStage stage);

    List<ExerciseScore> findByChildIdInAndExerciseStage(Collection<Long> childIds, ColourStage stage);
}
