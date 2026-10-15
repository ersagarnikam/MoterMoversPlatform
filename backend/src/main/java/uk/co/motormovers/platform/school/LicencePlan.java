package uk.co.motormovers.platform.school;

/** Proposed plans (see the partnership proposal, section 5). Prices are in pence per year. */
public enum LicencePlan {
    FREE("Free", 0),
    EARLY_YEARS("Early years", 45_000),
    PRIMARY_CORE("Primary core", 90_000),
    FULL_PROGRAMME("Full programme", 175_000),
    TRUST("Trust", 148_750),
    FREE_TRIAL("Free trial", 0);

    private final String label;
    private final int annualPricePence;

    LicencePlan(String label, int annualPricePence) {
        this.label = label;
        this.annualPricePence = annualPricePence;
    }

    public String label() {
        return label;
    }

    public int annualPricePence() {
        return annualPricePence;
    }
}
