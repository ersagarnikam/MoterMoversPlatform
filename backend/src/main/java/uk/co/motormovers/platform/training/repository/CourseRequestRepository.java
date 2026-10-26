package uk.co.motormovers.platform.training.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.CourseRequest;

public interface CourseRequestRepository extends JpaRepository<CourseRequest, Long> {

    List<CourseRequest> findBySchoolIdOrderByRequestedAtDesc(Long schoolId);

    List<CourseRequest> findByUserIdOrderByRequestedAtDesc(Long userId);

    Optional<CourseRequest> findByCohortIdAndUserIdAndStatus(Long cohortId, Long userId, CourseRequest.Status status);

    long countByCohortIdAndStatus(Long cohortId, CourseRequest.Status status);

    long countByStatus(CourseRequest.Status status);
}
