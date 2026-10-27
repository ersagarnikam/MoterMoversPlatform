package uk.co.motormovers.platform.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.common.model.ColourStage;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByStageOrderByNumber(ColourStage stage);

    boolean existsByStage(ColourStage stage);
}
