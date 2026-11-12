package uk.co.motormovers.platform.home.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A reminder sent before a family's pass ends (30 or 7 days) or on the day (0), so a restart never resends it. */
@Entity
@Table(name = "home_pass_reminder")
public class HomePassReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long familyId;

    private LocalDate endsOn;

    @Column(name = "days_before")
    private int reminderDays;

    private LocalDate sentOn;

    protected HomePassReminder() {
    }

    public HomePassReminder(Long familyId, LocalDate endsOn, int reminderDays, LocalDate sentOn) {
        this.familyId = familyId;
        this.endsOn = endsOn;
        this.reminderDays = reminderDays;
        this.sentOn = sentOn;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public int getReminderDays() {
        return reminderDays;
    }
}
