package uk.co.motormovers.platform.training.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.Enrolment;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

    long countByCohortId(Long cohortId);

    long countByCourseIdAndCohortIsNull(Long courseId);

    /** Someone's place on a recorded course (a place with no cohort). */
    Optional<Enrolment> findByCourseIdAndUserIdAndCohortIsNull(Long courseId, Long userId);

    /** Everyone with a place on a recorded course, for the team. */
    List<Enrolment> findByCourseIdAndCohortIsNullOrderByEnrolledAt(Long courseId);

    Optional<Enrolment> findByCohortIdAndUserId(Long cohortId, Long userId);

    List<Enrolment> findByUserIdOrderByEnrolledAtDesc(Long userId);

    List<Enrolment> findByCohortIdOrderByEnrolledAt(Long cohortId);

    List<Enrolment> findByCertificateExpiresOnBetweenAndExpiryReminderSentOnIsNull(LocalDate from, LocalDate to);

    List<Enrolment> findByCertificateExpiresOnBetweenOrderByCertificateExpiresOn(LocalDate from, LocalDate to);
}
