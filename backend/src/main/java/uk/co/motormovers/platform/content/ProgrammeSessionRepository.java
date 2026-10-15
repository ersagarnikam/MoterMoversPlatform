package uk.co.motormovers.platform.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.ColourStage;

public interface ProgrammeSessionRepository extends JpaRepository<ProgrammeSession, Long> {

    List<ProgrammeSession> findByStageAndStatusOrderByUnitAscDayAsc(ColourStage stage, ContentStatus status);

    List<ProgrammeSession> findAllByOrderByStageAscUnitAscDayAsc();
}
