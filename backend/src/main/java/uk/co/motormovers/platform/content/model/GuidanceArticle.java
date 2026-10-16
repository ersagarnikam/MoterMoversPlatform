package uk.co.motormovers.platform.content.model;

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

import uk.co.motormovers.platform.common.model.ColourStage;

/** A short read for parents on supporting movement at home. A null stage means it suits every stage. */
@Entity
@Table(name = "guidance_article")
public class GuidanceArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String summary;

    /** Plain text; paragraphs are separated by a blank line. */
    private String body;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private int minutesToRead;

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected GuidanceArticle() {
    }

    public GuidanceArticle(String title, String summary, String body, ColourStage stage, int minutesToRead) {
        this.title = title;
        this.summary = summary;
        this.body = body;
        this.stage = stage;
        this.minutesToRead = minutesToRead;
    }

    public List<String> paragraphs() {
        return Arrays.stream(body.split("\\n\\s*\\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** All wording a parent will read, for the wording check. */
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

    /** e.g. "All stages · Guidance" or "Stage 1 · Red · Guidance". */
    public String label() {
        return (stage == null ? "All stages" : stage.displayName()) + " · Guidance";
    }

    public Long getId() {
        return id;
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

    public ColourStage getStage() {
        return stage;
    }

    public int getMinutesToRead() {
        return minutesToRead;
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
