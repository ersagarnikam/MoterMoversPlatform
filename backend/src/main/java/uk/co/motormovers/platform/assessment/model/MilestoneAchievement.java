package uk.co.motormovers.platform.assessment.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.child.model.Child;

/**
 * A movement the child can do confidently and securely, with their age in months at the time (Pat: age in months
 * correlates better with the milestones than a date of birth). Once secure it stays secure: one row per child and
 * movement. {@code clientId} comes from the device, so a record sent twice is stored once.
 */
@Entity
@Table(name = "milestone_achievement")
public class MilestoneAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Milestone milestone;

    private int ageMonths;

    @Enumerated(EnumType.STRING)
    private YearPoint yearPoint;

    private int schoolYear;

    private LocalDate achievedOn;

    private String recordedBy;

    protected MilestoneAchievement() {
    }

    public MilestoneAchievement(String clientId, Child child, Milestone milestone, int ageMonths,
            YearPoint yearPoint, LocalDate achievedOn, String recordedBy) {
        this.clientId = clientId;
        this.child = child;
        this.milestone = milestone;
        this.ageMonths = ageMonths;
        this.yearPoint = yearPoint;
        this.schoolYear = YearPoint.schoolYear(achievedOn);
        this.achievedOn = achievedOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public Child getChild() {
        return child;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public int getAgeMonths() {
        return ageMonths;
    }

    public YearPoint getYearPoint() {
        return yearPoint;
    }

    public int getSchoolYear() {
        return schoolYear;
    }

    public LocalDate getAchievedOn() {
        return achievedOn;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
