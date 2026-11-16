package uk.co.motormovers.platform.content.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * One of the programme's films: an uploaded file's key, or a pasted link. It belongs either to a rhyme or module of a
 * colour ({@code unit}), where every session of it plays the film, or to one exercise of a colour's grid
 * ({@code exerciseId}), where each step of a session plays its own. It is kept apart from the sessions so that sessions
 * added later have it too.
 */
@Entity
@Table(name = "programme_film")
public class ProgrammeFilm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    /** The rhyme or module; null for an exercise's film. */
    private Integer unit;

    /** The grid exercise; null for a rhyme's or module's film. */
    private Long exerciseId;

    private String videoUrl;

    private String videoKey;

    /** The credit line shown under the film; null for the default. */
    private String credit;

    private String attachedBy;

    private Instant attachedAt;

    protected ProgrammeFilm() {
    }

    public static ProgrammeFilm forUnit(ColourStage stage, int unit) {
        ProgrammeFilm film = new ProgrammeFilm();
        film.stage = stage;
        film.unit = unit;
        return film;
    }

    public static ProgrammeFilm forExercise(ColourStage stage, Long exerciseId) {
        ProgrammeFilm film = new ProgrammeFilm();
        film.stage = stage;
        film.exerciseId = exerciseId;
        return film;
    }

    public void attach(String videoKey, String videoUrl, String credit, String by, Instant at) {
        this.videoKey = videoKey;
        this.videoUrl = videoUrl;
        this.credit = credit;
        this.attachedBy = by;
        this.attachedAt = at;
    }

    public Long getId() {
        return id;
    }

    public ColourStage getStage() {
        return stage;
    }

    public Integer getUnit() {
        return unit;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getVideoKey() {
        return videoKey;
    }

    public String getCredit() {
        return credit;
    }

    public String getAttachedBy() {
        return attachedBy;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }
}
