package uk.co.motormovers.platform.common;

/** The seven Motor Movers colour stages, in programme order (6 months to 8 years). */
public enum ColourStage {
    RED("Red"),
    ORANGE("Orange"),
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    INDIGO("Indigo"),
    VIOLET("Violet");

    private final String label;

    ColourStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int number() {
        return ordinal() + 1;
    }

    /** e.g. "Stage 3 · Yellow". */
    public String displayName() {
        return "Stage " + number() + " · " + label;
    }
}
