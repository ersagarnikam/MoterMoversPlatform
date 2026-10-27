package uk.co.motormovers.platform.assessment.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/** The two comments on Pat's summary sheet for a colour. The totals are worked out from the scores. */
@Entity
@Table(name = "colour_summary")
public class ColourSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long childId;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private String personalComment;

    private String academicComment;

    private LocalDate writtenOn;

    private String recordedBy;

    protected ColourSummary() {
    }

    public ColourSummary(Long childId, ColourStage stage) {
        this.childId = childId;
        this.stage = stage;
    }

    public void write(String personalComment, String academicComment, LocalDate writtenOn, String recordedBy) {
        this.personalComment = personalComment;
        this.academicComment = academicComment;
        this.writtenOn = writtenOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public ColourStage getStage() {
        return stage;
    }

    public String getPersonalComment() {
        return personalComment;
    }

    public String getAcademicComment() {
        return academicComment;
    }

    public LocalDate getWrittenOn() {
        return writtenOn;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
