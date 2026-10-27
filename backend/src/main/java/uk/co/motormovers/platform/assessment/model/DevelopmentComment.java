package uk.co.motormovers.platform.assessment.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The dated comment box under a chart, one per school year and point in the year (the booklet has three). */
@Entity
@Table(name = "development_comment")
public class DevelopmentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long childId;

    @Enumerated(EnumType.STRING)
    private Chart chart;

    private int schoolYear;

    @Enumerated(EnumType.STRING)
    private YearPoint yearPoint;

    private String note;

    private LocalDate writtenOn;

    private String recordedBy;

    protected DevelopmentComment() {
    }

    public DevelopmentComment(Long childId, Chart chart, int schoolYear, YearPoint yearPoint) {
        this.childId = childId;
        this.chart = chart;
        this.schoolYear = schoolYear;
        this.yearPoint = yearPoint;
    }

    public void write(String note, LocalDate writtenOn, String recordedBy) {
        this.note = note;
        this.writtenOn = writtenOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public Chart getChart() {
        return chart;
    }

    public int getSchoolYear() {
        return schoolYear;
    }

    public YearPoint getYearPoint() {
        return yearPoint;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getWrittenOn() {
        return writtenOn;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
