package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByPosition(Long quizId);

    long countByQuizId(Long quizId);

    void deleteByQuizId(Long quizId);
}
