package uk.co.motormovers.platform.content.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/** A short movement activity for a parent and child to do at home, in sequence within a colour stage. */
@Entity
@Table(name = "home_activity")
public class HomeActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private int position;

    private String title;

    private String focus;

    private int durationMinutes;

    private String videoUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "home_activity_step", joinColumns = @JoinColumn(name = "activity_id"))
    @OrderColumn(name = "position")
    @Column(name = "description")
    private List<String> steps = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected HomeActivity() {
    }

    public HomeActivity(ColourStage stage, int position, String title, String focus, int durationMinutes,
            String videoUrl, List<String> steps) {
        this.stage = stage;
        this.position = position;
        this.title = title;
        this.focus = focus;
        this.durationMinutes = durationMinutes;
        this.videoUrl = videoUrl;
        this.steps = new ArrayList<>(steps);
    }

    /** All wording a parent will read, for the wording check. */
    public List<String> wording() {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.add(focus);
        text.addAll(steps);
        return text;
    }

    public void review(ContentStatus outcome, String reviewer) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    /** e.g. "Stage 3 · Yellow · Home activity 2". */
    public String label() {
        return stage.displayName() + " · Home activity " + position;
    }

    public Long getId() {
        return id;
    }

    public ColourStage getStage() {
        return stage;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getFocus() {
        return focus;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public List<String> getSteps() {
        return steps;
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
