package uk.co.motormovers.platform.training.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.Cohort;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByCourseIdAndStartsOnGreaterThanEqualOrderByStartsOn(Long courseId, LocalDate from);

    List<Cohort> findByCourseIdOrderByStartsOn(Long courseId);
}
