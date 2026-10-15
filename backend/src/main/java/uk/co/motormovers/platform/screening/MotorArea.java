package uk.co.motormovers.platform.screening;

/** The six areas the readiness profile groups results into. */
public enum MotorArea {
    BALANCE("Balance"),
    GROSS_MOTOR("Gross motor"),
    MANUAL_DEXTERITY("Manual dexterity"),
    AIMING_AND_CATCHING("Aiming and catching"),
    POSTURAL_CONTROL("Postural control"),
    BILATERAL_COORDINATION("Bilateral coordination");

    private final String label;

    MotorArea(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
