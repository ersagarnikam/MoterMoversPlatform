package uk.co.motormovers.platform.child.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.school.model.SchoolClass;

/**
 * A child on the programme. Held by initials and year group by default; a full name is stored only
 * when the school has switched full names on. No date of birth is held.
 *
 * <p>A child belongs to a school class, or (Motor Movers at Home) to a family: then {@code fullName} is the name the
 * parent chose, and the age in months on a given day is kept instead of a year group.
 */
@Entity
public class Child {

    /** What a home child has in place of a year group. */
    public static final String HOME_YEAR_GROUP = "At home";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SchoolClass schoolClass;

    private Long familyId;

    private Integer ageMonths;

    private LocalDate ageRecordedOn;

    private LocalDate homeCheckOn;

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

    /** A family's own child, not on a school roll. */
    public static Child atHome(Long familyId, String name, int ageMonths, LocalDate today) {
        Child child = new Child();
        child.familyId = familyId;
        child.yearGroup = HOME_YEAR_GROUP;
        child.createdAt = Instant.now();
        child.rename(name);
        child.setAge(ageMonths, today);
        return child;
    }

    public boolean isAtHome() {
        return schoolClass == null && familyId != null;
    }

    /** Name shown to staff: full name only where the school allows it. A home child: the name the parent chose. */
    public String displayName() {
        if (schoolClass == null) {
            return fullName != null ? fullName : initials;
        }
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

    /** Only shown where the school has switched full names on. */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void rename(String name) {
        this.fullName = name;
        this.initials = name.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    public void setAge(int ageMonths, LocalDate on) {
        this.ageMonths = ageMonths;
        this.ageRecordedOn = on;
    }

    /** A home child's age today, from the age the parent gave and the time since; null for a school child. */
    public Integer ageMonthsOn(LocalDate today) {
        if (ageMonths == null || ageRecordedOn == null) {
            return null;
        }
        return ageMonths + (int) ChronoUnit.MONTHS.between(ageRecordedOn, today);
    }

    public void checkedOn(LocalDate date) {
        this.homeCheckOn = date;
    }

    public void moveTo(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
        this.yearGroup = schoolClass.getYearGroup();
    }

    public Long getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public LocalDate getHomeCheckOn() {
        return homeCheckOn;
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
