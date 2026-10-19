package uk.co.motormovers.platform.school.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * A licence plan the team sells, such as "Primary core". Its price is not stored here: prices are dated
 * (LicencePlanPrice) so a change never alters what earlier licences were sold at. A retired plan stays on
 * existing licences but cannot be chosen for new ones.
 */
@Entity
public class LicencePlan {

    /** The plan that makes a licence a free trial. */
    public static final String FREE_TRIAL = "FREE_TRIAL";

    @Id
    private String code;

    private String label;

    private boolean freeTrial;

    private boolean active;

    private int sortOrder;

    protected LicencePlan() {
    }

    public LicencePlan(String code, String label, boolean freeTrial, int sortOrder) {
        this.code = code;
        this.label = label;
        this.freeTrial = freeTrial;
        this.active = true;
        this.sortOrder = sortOrder;
    }

    public void rename(String label) {
        this.label = label;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFreeTrial() {
        return freeTrial;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
