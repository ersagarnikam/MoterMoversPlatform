package uk.co.motormovers.platform.training.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.content.model.ContentStatus;

/** One part of the one-hour induction that school staff complete before running sessions. */
@Entity
@Table(name = "induction_lesson")
public class InductionLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int position;

    private String title;

    private String summary;

    /** Plain text; paragraphs are separated by a blank line. */
    private String body;

    private int minutes;

    private String videoUrl;

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected InductionLesson() {
    }

    public InductionLesson(int position, String title, String summary, String body, int minutes, String videoUrl) {
        this.position = position;
        this.title = title;
        this.summary = summary;
        this.body = body;
        this.minutes = minutes;
        this.videoUrl = videoUrl;
    }

    public List<String> paragraphs() {
        return Arrays.stream(body.split("\\n\\s*\\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** All wording a teacher will read, for the wording check. */
    public List<String> wording() {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.add(summary);
        text.addAll(paragraphs());
        return text;
    }

    public void review(ContentStatus outcome, String reviewer) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    /** e.g. "Induction · Part 2". */
    public String label() {
        return "Induction · Part " + position;
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
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
