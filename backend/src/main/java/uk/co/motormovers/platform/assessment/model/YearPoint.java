package uk.co.motormovers.platform.assessment.model;

/**
 * When in the school year the developmental assessment is done: the start, middle or end. Which months those are
 * depends on the country (assessment/util/SchoolYear).
 */
public enum YearPoint {
    START("Start of year"),
    MIDDLE("Middle of year"),
    END("End of year");

    private final String label;

    YearPoint(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
