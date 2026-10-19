package uk.co.motormovers.platform.payment.model;

/** What a payment is for. */
public enum PaymentPurpose {
    /** A school's yearly programme licence. */
    LICENCE("Licence"),
    /** A live course: the practitioner course, the parent course, or a renewal. */
    COURSE("Course"),
    /** Anything else, such as printed materials or a training day. */
    OTHER("Other");

    private final String label;

    PaymentPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
