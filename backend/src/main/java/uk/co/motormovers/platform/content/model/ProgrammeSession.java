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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/** One daily 10-minute, video-led session: stage, unit and day within the unit. */
@Entity
@Table(name = "programme_session")
public class ProgrammeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private int unit;

    @Column(name = "day_number")
    private int day;

    private String title;

    private String focus;

    private int durationMinutes;

    private String videoUrl;

    /** The nursery rhyme sung in this session (Red, Orange and Yellow stages). */
    @ManyToOne(fetch = FetchType.LAZY)
    private LibraryResource rhyme;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "programme_session_activity", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position")
    @Column(name = "description")
    private List<String> activities = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    private String reviewedBy;

    private Instant reviewedAt;

    protected ProgrammeSession() {
    }

    public ProgrammeSession(ColourStage stage, int unit, int day, String title, String focus, int durationMinutes,
            String videoUrl, List<String> activities) {
        this.stage = stage;
        this.unit = unit;
        this.day = day;
        this.title = title;
        this.focus = focus;
        this.durationMinutes = durationMinutes;
        this.videoUrl = videoUrl;
        this.activities = new ArrayList<>(activities);
    }

    /** All wording a teacher or parent will read, for the wording check. */
    public List<String> wording() {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.add(focus);
        text.addAll(activities);
        return text;
    }

    public void review(ContentStatus outcome, String reviewer) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    /** e.g. "Stage 3 · Yellow · Module 2 · Day 8". The schema calls a module a unit. */
    public String label() {
        return stage.displayName() + " · Module " + unit + " · Day " + day;
    }

    public Long getId() {
        return id;
    }

    public ColourStage getStage() {
        return stage;
    }

    public int getUnit() {
        return unit;
    }

    public int getDay() {
        return day;
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

    public LibraryResource getRhyme() {
        return rhyme;
    }

    public void setRhyme(LibraryResource rhyme) {
        this.rhyme = rhyme;
    }

    public List<String> getActivities() {
        return activities;
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
