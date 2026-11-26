package uk.co.motormovers.platform.school.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
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
        /** Not started yet: it runs from its start date (task 88). */
        UPCOMING, ACTIVE, RENEWAL_DUE, FREE_TRIAL, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan")
    private LicencePlan plan;

    /** What the school pays each year: the plan price when the licence was sold, unless another was agreed. */
    private int agreedPricePence;

    private LocalDate startsOn;

    private LocalDate endsOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "licence_stage", joinColumns = @JoinColumn(name = "licence_id"))
    @Column(name = "stage")
    @Enumerated(EnumType.STRING)
    private Set<ColourStage> stages = EnumSet.noneOf(ColourStage.class);

    protected Licence() {
    }

    public Licence(School school, LicencePlan plan, int agreedPricePence, LocalDate startsOn, LocalDate endsOn,
            Set<ColourStage> stages) {
        this.school = school;
        this.plan = plan;
        this.agreedPricePence = agreedPricePence;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.stages = stages.isEmpty() ? EnumSet.noneOf(ColourStage.class) : EnumSet.copyOf(stages);
    }

    /**
     * The licence a school's status is shown from, where it has several: the one running today (the latest to end,
     * if more than one), else the next to start, else the last that ended. Whether a colour is open is another
     * question, answered from every licence: {@code LicenceService.licensedFor}.
     */
    public static Optional<Licence> shownOn(Collection<Licence> all, LocalDate today) {
        Optional<Licence> running = all.stream().filter(l -> l.isValidOn(today))
                .max(Comparator.comparing(Licence::getEndsOn));
        if (running.isPresent()) {
            return running;
        }
        Optional<Licence> next = all.stream().filter(l -> today.isBefore(l.getStartsOn()))
                .min(Comparator.comparing(Licence::getStartsOn));
        return next.isPresent() ? next : all.stream().max(Comparator.comparing(Licence::getEndsOn));
    }

    public Status statusOn(LocalDate today) {
        // The same rule as isValidOn: before its start it does not run, whatever its plan.
        if (today.isBefore(startsOn)) {
            return Status.UPCOMING;
        }
        if (today.isAfter(endsOn)) {
            return Status.EXPIRED;
        }
        if (plan.isFreeTrial()) {
            return Status.FREE_TRIAL;
        }
        if (!today.plusDays(RENEWAL_WINDOW_DAYS).isBefore(endsOn)) {
            return Status.RENEWAL_DUE;
        }
        return Status.ACTIVE;
    }

    /** Whether the licence runs on the day: from its start to its end date, both included. */
    public boolean isValidOn(LocalDate day) {
        return !day.isBefore(startsOn) && !day.isAfter(endsOn);
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

    public int getAgreedPricePence() {
        return agreedPricePence;
    }

    public void agreePrice(int pence) {
        this.agreedPricePence = pence;
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
