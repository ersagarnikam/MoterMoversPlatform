package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.CourseQuiz;

public interface CourseQuizRepository extends JpaRepository<CourseQuiz, Long> {

    List<CourseQuiz> findByCourseIdOrderByAfterSessionAscIdAsc(Long courseId);
}
