package uk.co.motormovers.platform.training.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import uk.co.motormovers.platform.training.model.CohortSession;

public interface CohortSessionRepository extends JpaRepository<CohortSession, Long> {

    List<CohortSession> findByCohortIdOrderByNumber(Long cohortId);

    List<CohortSession> findByCohortIdInAndStartsAtAfterOrderByStartsAt(Collection<Long> cohortIds, Instant after);

    List<CohortSession> findByStartsAtAfterOrderByStartsAt(Instant after);

    List<CohortSession> findByStartsAtBetweenAndReminderSentAtIsNullOrderByStartsAt(Instant from, Instant to);

    /** Locks the row, so two people joining at once do not create two Zoom meetings. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CohortSession s where s.id = :id")
    Optional<CohortSession> findForUpdate(Long id);
}
