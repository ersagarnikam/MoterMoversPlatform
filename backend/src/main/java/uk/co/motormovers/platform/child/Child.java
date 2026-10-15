package uk.co.motormovers.platform.child;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.school.SchoolClass;

/**
 * A child on the programme. Held by initials and year group by default; a full name is stored only
 * when the school has switched full names on. No date of birth is held.
 */
@Entity
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SchoolClass schoolClass;

    private String initials;

    private String fullName;

    private String yearGroup;

    @Enumerated(EnumType.STRING)
    private ColourStage startingStage;

    @Enumerated(EnumType.STRING)
    private ColourStage currentStage;

    private boolean videoConsent;

    private LocalDate leftOn;

    private Instant createdAt;

    protected Child() {
    }

    public Child(SchoolClass schoolClass, String initials, String yearGroup) {
        this.schoolClass = schoolClass;
        this.initials = initials;
        this.yearGroup = yearGroup;
        this.createdAt = Instant.now();
    }

    /** Name shown to staff: full name only where the school allows it. */
    public String displayName() {
        boolean fullNames = schoolClass.getSchool().isFullNamesEnabled();
        return fullNames && fullName != null ? fullName : initials;
    }

    public void placeAt(ColourStage stage) {
        if (startingStage == null) {
            startingStage = stage;
        }
        currentStage = stage;
    }

    public boolean hasMovedUp() {
        return startingStage != null && currentStage != null && currentStage.compareTo(startingStage) > 0;
    }

    public void markLeft(LocalDate date) {
        this.leftOn = date;
    }

    public Long getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public String getInitials() {
        return initials;
    }

    public String getFullName() {
        return fullName;
    }

    public String getYearGroup() {
        return yearGroup;
    }

    public ColourStage getStartingStage() {
        return startingStage;
    }

    public ColourStage getCurrentStage() {
        return currentStage;
    }

    public boolean isVideoConsent() {
        return videoConsent;
    }

    public LocalDate getLeftOn() {
        return leftOn;
    }
}
