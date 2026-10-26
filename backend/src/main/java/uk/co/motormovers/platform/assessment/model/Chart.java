package uk.co.motormovers.platform.assessment.model;

/** The two developmental charts in Pat's assessment booklet. */
public enum Chart {
    FINE("Fine motor"),
    GROSS("Gross motor");

    private final String label;

    Chart(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
