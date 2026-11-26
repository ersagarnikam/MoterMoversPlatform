package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.CourseLessonProgress;

public interface CourseLessonProgressRepository extends JpaRepository<CourseLessonProgress, Long> {

    List<CourseLessonProgress> findByEnrolmentId(Long enrolmentId);

    boolean existsByEnrolmentIdAndLessonId(Long enrolmentId, Long lessonId);

    long countByEnrolmentId(Long enrolmentId);

    long countByLessonId(Long lessonId);
}
