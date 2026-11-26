package uk.co.motormovers.platform.training.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A lesson of a recorded course that a learner has done, for their place on the course (go-live task 89). */
@Entity
@Table(name = "course_lesson_progress")
public class CourseLessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long enrolmentId;

    private Long lessonId;

    private Instant completedAt;

    protected CourseLessonProgress() {
    }

    public CourseLessonProgress(Long enrolmentId, Long lessonId, Instant completedAt) {
        this.enrolmentId = enrolmentId;
        this.lessonId = lessonId;
        this.completedAt = completedAt;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
