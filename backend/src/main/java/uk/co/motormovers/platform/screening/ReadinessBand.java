package uk.co.motormovers.platform.screening;

/** Programme terms only: never centiles or condition names. Pat and Laura approve the wording. */
public enum ReadinessBand {
    EMERGING("Emerging"),
    DEVELOPING("Developing"),
    SECURE("Secure");

    private final String label;

    ReadinessBand(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
