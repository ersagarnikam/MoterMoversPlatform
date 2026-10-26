package uk.co.motormovers.platform.assessment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.DevelopmentComment;
import uk.co.motormovers.platform.assessment.model.YearPoint;

public interface DevelopmentCommentRepository extends JpaRepository<DevelopmentComment, Long> {

    List<DevelopmentComment> findByChildIdOrderBySchoolYearAscYearPointAsc(Long childId);

    Optional<DevelopmentComment> findByChildIdAndChartAndSchoolYearAndYearPoint(Long childId, Chart chart,
            int schoolYear, YearPoint yearPoint);
}
