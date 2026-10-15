package uk.co.motormovers.platform.training;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

    long countByCohortId(Long cohortId);

    Optional<Enrolment> findByCohortIdAndUserId(Long cohortId, Long userId);

    List<Enrolment> findByUserIdOrderByEnrolledAtDesc(Long userId);

    List<Enrolment> findByCohortIdOrderByEnrolledAt(Long cohortId);
}
