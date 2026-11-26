package uk.co.motormovers.platform.training.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.content.model.ContentStatus;

/**
 * One lesson of a recorded course (go-live task 89): a film, with optional text (for example the script) and
 * optional slides. The team writes it as a draft and approves it, with the wording check, like an induction lesson;
 * learners see approved lessons only, in order.
 */
@Entity
@Table(name = "course_lesson")
public class CourseLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    private int position;

    private String title;

    /** Plain text; paragraphs are separated by a blank line. May be empty. */
    private String body;

    private int minutes;

    private String videoUrl;

    private String videoKey;

    private String slidesUrl;

    private String slidesKey;

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected CourseLesson() {
    }

    public CourseLesson(Course course, int position, String title, String body, int minutes) {
        this.course = course;
        revise(position, title, body, minutes);
    }

    /** A lesson that is changed goes back to a draft, to be checked and approved again. */
    public void revise(int position, String title, String body, int minutes) {
        this.position = position;
        this.title = title.strip();
        this.body = body == null ? "" : body.strip();
        this.minutes = minutes;
        this.status = ContentStatus.DRAFT;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    public void film(String url, String key) {
        this.videoUrl = blankToNull(url);
        this.videoKey = blankToNull(key);
    }

    public void slides(String url, String key) {
        this.slidesUrl = blankToNull(url);
        this.slidesKey = blankToNull(key);
    }

    public boolean hasFilm() {
        return videoUrl != null || videoKey != null;
    }

    public List<String> paragraphs() {
        return Arrays.stream(body.split("\\n\\s*\\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** All wording a learner will read, for the wording check. */
    public List<String> wording() {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.addAll(paragraphs());
        return text;
    }

    public void review(ContentStatus outcome, String reviewer, Instant at) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getVideoKey() {
        return videoKey;
    }

    public String getSlidesUrl() {
        return slidesUrl;
    }

    public String getSlidesKey() {
        return slidesKey;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
