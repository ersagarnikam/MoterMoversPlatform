package uk.co.motormovers.platform.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private CourseTrack track;

    private String title;

    private String description;

    private int liveSessions;

    private int sessionMinutes;

    private int pricePence;

    private Integer renewalPence;

    private boolean awardsCertificate;

    protected Course() {
    }

    public Course(CourseTrack track, String title, String description, int liveSessions, int sessionMinutes,
            int pricePence, Integer renewalPence, boolean awardsCertificate) {
        this.track = track;
        this.title = title;
        this.description = description;
        this.liveSessions = liveSessions;
        this.sessionMinutes = sessionMinutes;
        this.pricePence = pricePence;
        this.renewalPence = renewalPence;
        this.awardsCertificate = awardsCertificate;
    }

    /** Everything but the track, which decides who may book and stays fixed once the course exists. */
    public void revise(String title, String description, int liveSessions, int sessionMinutes, int pricePence,
            Integer renewalPence, boolean awardsCertificate) {
        this.title = title;
        this.description = description;
        this.liveSessions = liveSessions;
        this.sessionMinutes = sessionMinutes;
        this.pricePence = pricePence;
        this.renewalPence = renewalPence;
        this.awardsCertificate = awardsCertificate;
    }

    public Long getId() {
        return id;
    }

    public CourseTrack getTrack() {
        return track;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getLiveSessions() {
        return liveSessions;
    }

    public int getSessionMinutes() {
        return sessionMinutes;
    }

    public int getPricePence() {
        return pricePence;
    }

    public Integer getRenewalPence() {
        return renewalPence;
    }

    public boolean isAwardsCertificate() {
        return awardsCertificate;
    }
}
