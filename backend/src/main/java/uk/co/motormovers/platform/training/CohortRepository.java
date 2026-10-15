package uk.co.motormovers.platform.training;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByCourseIdAndStartsOnGreaterThanEqualOrderByStartsOn(Long courseId, LocalDate from);
}
