package uk.co.motormovers.platform.classroom;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.ColourStage;

public interface DailySessionRecordRepository extends JpaRepository<DailySessionRecord, Long> {

    Optional<DailySessionRecord> findBySchoolClassIdAndSessionDate(Long schoolClassId, LocalDate date);

    List<DailySessionRecord> findBySchoolClassIdInAndSessionDateBetween(Collection<Long> schoolClassIds,
            LocalDate from, LocalDate to);

    long countBySchoolClassIdAndSessionStage(Long schoolClassId, ColourStage stage);
}
