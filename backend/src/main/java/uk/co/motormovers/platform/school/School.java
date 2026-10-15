package uk.co.motormovers.platform.school;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import uk.co.motormovers.platform.common.Region;

/** A school or nursery setting. Privacy switches are off by default; the school must opt in. */
@Entity
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Trust trust;

    @Enumerated(EnumType.STRING)
    private Region region;

    private boolean fullNamesEnabled;

    private boolean videoEnabled;

    private boolean trustCanSeeChildren;

    protected School() {
    }

    public School(String name, Trust trust, Region region) {
        this.name = name;
        this.trust = trust;
        this.region = region;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Trust getTrust() {
        return trust;
    }

    public Region getRegion() {
        return region;
    }

    public boolean isFullNamesEnabled() {
        return fullNamesEnabled;
    }

    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    public boolean isTrustCanSeeChildren() {
        return trustCanSeeChildren;
    }
}
