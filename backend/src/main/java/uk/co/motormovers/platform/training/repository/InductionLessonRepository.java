package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.training.model.InductionLesson;

public interface InductionLessonRepository extends JpaRepository<InductionLesson, Long> {

    List<InductionLesson> findByStatusOrderByPosition(ContentStatus status);

    List<InductionLesson> findAllByOrderByPosition();
}
