package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByEnrolmentId(Long enrolmentId);

    long countByQuizId(Long quizId);

    boolean existsByQuizIdAndEnrolmentIdAndPassedTrue(Long quizId, Long enrolmentId);
}
