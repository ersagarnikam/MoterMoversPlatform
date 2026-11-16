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
 * The film for every session of one rhyme or module of a colour: an uploaded file's key, or a pasted link. It is kept
 * apart from the sessions so that sessions of the unit added later have it too.
 */
@Entity
@Table(name = "unit_film")
public class UnitFilm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private int unit;

    private String videoUrl;

    private String videoKey;

    /** The credit line shown under the film; null until the team has typed one. */
    private String credit;

    private String attachedBy;

    private Instant attachedAt;

    protected UnitFilm() {
    }

    public UnitFilm(ColourStage stage, int unit) {
        this.stage = stage;
        this.unit = unit;
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

    public int getUnit() {
        return unit;
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
