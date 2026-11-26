package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.training.model.CourseLesson;

public interface CourseLessonRepository extends JpaRepository<CourseLesson, Long> {

    List<CourseLesson> findByCourseIdOrderByPositionAscIdAsc(Long courseId);

    /** What learners see: the approved lessons, in order. */
    List<CourseLesson> findByCourseIdAndStatusOrderByPositionAscIdAsc(Long courseId, ContentStatus status);

    long countByCourseIdAndStatus(Long courseId, ContentStatus status);
}
