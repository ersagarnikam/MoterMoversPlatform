package uk.co.motormovers.platform.training.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.Enrolment;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

    long countByCohortId(Long cohortId);

    Optional<Enrolment> findByCohortIdAndUserId(Long cohortId, Long userId);

    List<Enrolment> findByUserIdOrderByEnrolledAtDesc(Long userId);

    List<Enrolment> findByCohortIdOrderByEnrolledAt(Long cohortId);

    List<Enrolment> findByCertificateExpiresOnBetweenAndExpiryReminderSentOnIsNull(LocalDate from, LocalDate to);
}
