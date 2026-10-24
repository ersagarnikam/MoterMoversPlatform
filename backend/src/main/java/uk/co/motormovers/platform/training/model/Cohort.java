package uk.co.motormovers.platform.training.model;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import uk.co.motormovers.platform.user.model.AppUser;

/** One run of a course: a start date, a time slot (UK evening or UK morning / AU evening) and a trainer. */
@Entity
public class Cohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    private String name;

    private LocalDate startsOn;

    private String timeSlot;

    /** When each live session starts, in the region's time (UK time for a UK deployment). */
    private LocalTime startTime;

    private int capacity;

    private String trainerName;

    /** The account that runs this cohort's live sessions (a trainer or the team). Null: the team only. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser trainer;

    private boolean recordingAgreed;

    protected Cohort() {
    }

    public Cohort(Course course, String name, LocalDate startsOn, LocalTime startTime, String timeSlot, int capacity,
            String trainerName, boolean recordingAgreed) {
        this.course = course;
        this.name = name;
        this.startsOn = startsOn;
        this.startTime = startTime;
        this.timeSlot = timeSlot;
        this.capacity = capacity;
        this.trainerName = trainerName;
        this.recordingAgreed = recordingAgreed;
    }

    public void revise(String name, LocalDate startsOn, LocalTime startTime, String timeSlot, int capacity,
            String trainerName, boolean recordingAgreed) {
        this.name = name;
        this.startsOn = startsOn;
        this.startTime = startTime;
        this.timeSlot = timeSlot;
        this.capacity = capacity;
        this.trainerName = trainerName;
        this.recordingAgreed = recordingAgreed;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public int getCapacity() {
        return capacity;
    }

    /** Makes {@code trainer} this cohort's trainer, shown to learners by name; null leaves it to the team. */
    public void assignTrainer(AppUser trainer) {
        this.trainer = trainer;
        if (trainer != null) {
            this.trainerName = trainer.getDisplayName();
        }
    }

    public AppUser getTrainer() {
        return trainer;
    }

    public String getTrainerName() {
        return trainerName;
    }

    public boolean isRecordingAgreed() {
        return recordingAgreed;
    }
}
