package uk.co.motormovers.platform.school.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

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

import uk.co.motormovers.platform.common.model.ColourStage;

/** An annual licence. Only the colour stages on the licence open in the school. */
@Entity
public class Licence {

    /** A licence ending within this many days is shown as "Renewal due". */
    public static final int RENEWAL_WINDOW_DAYS = 60;

    public enum Status {
        ACTIVE, RENEWAL_DUE, FREE_TRIAL, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private School school;

    @Enumerated(EnumType.STRING)
    private LicencePlan plan;

    private LocalDate startsOn;

    private LocalDate endsOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "licence_stage", joinColumns = @JoinColumn(name = "licence_id"))
    @Column(name = "stage")
    @Enumerated(EnumType.STRING)
    private Set<ColourStage> stages = EnumSet.noneOf(ColourStage.class);

    protected Licence() {
    }

    public Licence(School school, LicencePlan plan, LocalDate startsOn, LocalDate endsOn, Set<ColourStage> stages) {
        this.school = school;
        this.plan = plan;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.stages = stages.isEmpty() ? EnumSet.noneOf(ColourStage.class) : EnumSet.copyOf(stages);
    }

    public Status statusOn(LocalDate today) {
        if (today.isAfter(endsOn)) {
            return Status.EXPIRED;
        }
        if (plan == LicencePlan.FREE_TRIAL) {
            return Status.FREE_TRIAL;
        }
        if (!today.plusDays(RENEWAL_WINDOW_DAYS).isBefore(endsOn)) {
            return Status.RENEWAL_DUE;
        }
        return Status.ACTIVE;
    }

    public boolean covers(ColourStage stage) {
        return stages.contains(stage);
    }

    public Long getId() {
        return id;
    }

    public School getSchool() {
        return school;
    }

    public LicencePlan getPlan() {
        return plan;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public Set<ColourStage> getStages() {
        return stages;
    }
}
