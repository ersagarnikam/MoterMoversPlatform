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

/**
 * A file in the resource library: a rhyme recording, the rhyme book or a printable. A null stage means it
 * suits every stage. Until files are uploaded the URL is empty and the web app says the file is on its way.
 */
@Entity
@Table(name = "library_resource")
public class LibraryResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ResourceKind kind;

    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private String fileUrl;

    /** A rhyme's words, one line per line. */
    private String words;

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected LibraryResource() {
    }

    public LibraryResource(ResourceKind kind, String title, String description, ColourStage stage, String fileUrl,
            String words) {
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.stage = stage;
        this.fileUrl = fileUrl;
        this.words = words;
    }

    public List<String> lines() {
        return words == null ? List.of()
                : Arrays.stream(words.split("\\n")).map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    /** All wording a teacher will read, for the wording check. */
    public List<String> wording() {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.add(description);
        text.addAll(lines());
        return text;
    }

    public void review(ContentStatus outcome, String reviewer) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public boolean isApproved() {
        return status == ContentStatus.APPROVED;
    }

    /** e.g. "Stage 1 · Red · Rhyme" or "All stages · Book". */
    public String label() {
        String kindLabel = switch (kind) {
            case RHYME -> "Rhyme";
            case BOOK -> "Book";
            case PRINTABLE -> "Printable";
        };
        return (stage == null ? "All stages" : stage.displayName()) + " · " + kindLabel;
    }

    public Long getId() {
        return id;
    }

    public ResourceKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ColourStage getStage() {
        return stage;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getWords() {
        return words;
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
