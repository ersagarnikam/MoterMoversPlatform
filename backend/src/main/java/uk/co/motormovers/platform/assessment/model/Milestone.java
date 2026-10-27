package uk.co.motormovers.platform.assessment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One movement on a developmental chart, with its indicative age ("6 to 12 months" is 6 to 12). */
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Chart chart;

    private int position;

    private String band;

    private int ageFromMonths;

    private int ageToMonths;

    private String description;

    protected Milestone() {
    }

    public Milestone(Chart chart, int position, String band, int ageFromMonths, int ageToMonths, String description) {
        this.chart = chart;
        this.position = position;
        this.band = band;
        this.ageFromMonths = ageFromMonths;
        this.ageToMonths = ageToMonths;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Chart getChart() {
        return chart;
    }

    public int getPosition() {
        return position;
    }

    public String getBand() {
        return band;
    }

    public int getAgeFromMonths() {
        return ageFromMonths;
    }

    public int getAgeToMonths() {
        return ageToMonths;
    }

    public String getDescription() {
        return description;
    }
}
