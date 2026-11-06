package uk.co.motormovers.platform.training.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.content.model.ContentStatus;

/**
 * One part of an induction: the one-hour one that school staff complete before running sessions, or the short one
 * parents complete before their first home session.
 */
@Entity
@Table(name = "induction_lesson")
public class InductionLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private InductionAudience audience = InductionAudience.STAFF;

    /** The part number, counted within the audience. */
    @Column(name = "part")
    private int position;

    private String title;

    private String summary;

    /** Plain text; paragraphs are separated by a blank line. */
    private String body;

    private int minutes;

    private String videoUrl;

    private String videoKey;

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected InductionLesson() {
    }

    public InductionLesson(int position, String title, String summary, String body, int minutes, String videoUrl) {
        this(InductionAudience.STAFF, position, title, summary, body, minutes, videoUrl);
    }

    public InductionLesson(InductionAudience audience, int position, String title, String summary, String body,
            int minutes, String videoUrl) {
        this.audience = audience;
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

    /** e.g. "Induction · Part 2", "Parent induction · Part 1". */
    public String label() {
        return (audience == InductionAudience.PARENT ? "Parent induction" : "Induction") + " · Part " + position;
    }

    public InductionAudience getAudience() {
        return audience;
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

    public String getVideoKey() {
        return videoKey;
    }

    public void setVideoKey(String videoKey) {
        this.videoKey = videoKey;
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
