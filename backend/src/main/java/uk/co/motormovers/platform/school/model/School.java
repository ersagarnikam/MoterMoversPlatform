package uk.co.motormovers.platform.school.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import uk.co.motormovers.platform.common.model.Region;

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

    private boolean parentLinksEnabled;

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

    /** Whether staff may invite parents to see their child's progress in the parent app. */
    public boolean isParentLinksEnabled() {
        return parentLinksEnabled;
    }

    public void setParentLinksEnabled(boolean parentLinksEnabled) {
        this.parentLinksEnabled = parentLinksEnabled;
    }

    public void setPrivacy(boolean fullNamesEnabled, boolean videoEnabled, boolean trustCanSeeChildren) {
        this.fullNamesEnabled = fullNamesEnabled;
        this.videoEnabled = videoEnabled;
        this.trustCanSeeChildren = trustCanSeeChildren;
    }
}
