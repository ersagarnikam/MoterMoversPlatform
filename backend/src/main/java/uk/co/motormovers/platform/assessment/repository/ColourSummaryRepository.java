package uk.co.motormovers.platform.assessment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.ColourSummary;
import uk.co.motormovers.platform.common.model.ColourStage;

public interface ColourSummaryRepository extends JpaRepository<ColourSummary, Long> {

    Optional<ColourSummary> findByChildIdAndStage(Long childId, ColourStage stage);
}
