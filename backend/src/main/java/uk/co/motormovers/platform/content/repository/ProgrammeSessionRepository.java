package uk.co.motormovers.platform.content.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;

public interface ProgrammeSessionRepository extends JpaRepository<ProgrammeSession, Long> {

    List<ProgrammeSession> findByStageAndStatusOrderByUnitAscDayAsc(ColourStage stage, ContentStatus status);

    /** One place in a colour's approved sequence: the paging is what keeps today's screen from loading them all. */
    List<ProgrammeSession> findByStageAndStatus(ColourStage stage, ContentStatus status, Pageable page);

    long countByStageAndStatus(ColourStage stage, ContentStatus status);

    List<ProgrammeSession> findByStage(ColourStage stage);

    List<ProgrammeSession> findAllByOrderByStageAscUnitAscDayAsc();

    long countByStatus(ContentStatus status);
}
