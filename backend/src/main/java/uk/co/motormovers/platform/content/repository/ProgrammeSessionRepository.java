package uk.co.motormovers.platform.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;

public interface ProgrammeSessionRepository extends JpaRepository<ProgrammeSession, Long> {

    List<ProgrammeSession> findByStageAndStatusOrderByUnitAscDayAsc(ColourStage stage, ContentStatus status);

    List<ProgrammeSession> findAllByOrderByStageAscUnitAscDayAsc();

    long countByStatus(ContentStatus status);
}
