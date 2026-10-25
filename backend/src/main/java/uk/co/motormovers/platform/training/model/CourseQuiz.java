package uk.co.motormovers.platform.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * A short quiz between live sessions. It opens once session {@code afterSession} has started; learners may retake
 * it, and a course with quizzes needs every one passed before its certificate.
 */
@Entity
public class CourseQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    private int afterSession;

    private String title;

    private int passPercent;

    protected CourseQuiz() {
    }

    public CourseQuiz(Course course, int afterSession, String title, int passPercent) {
        this.course = course;
        revise(afterSession, title, passPercent);
    }

    public void revise(int afterSession, String title, int passPercent) {
        this.afterSession = afterSession;
        this.title = title;
        this.passPercent = passPercent;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public int getAfterSession() {
        return afterSession;
    }

    public String getTitle() {
        return title;
    }

    public int getPassPercent() {
        return passPercent;
    }
}
