package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import uk.co.motormovers.platform.training.model.SessionAttendance;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, SessionAttendance.Key> {

    long countByIdEnrolmentId(Long enrolmentId);

    boolean existsByIdSessionId(Long sessionId);

    List<SessionAttendance> findByIdSessionId(Long sessionId);

    /** The numbers of the sessions a learner was marked present at, e.g. [1, 2, 4]. */
    @Query("select s.number from SessionAttendance a, CohortSession s"
            + " where a.id.sessionId = s.id and a.id.enrolmentId = :enrolmentId order by s.number")
    List<Integer> attendedNumbers(Long enrolmentId);
}
