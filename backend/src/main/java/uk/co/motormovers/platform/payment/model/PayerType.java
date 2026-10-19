package uk.co.motormovers.platform.payment.model;

import uk.co.motormovers.platform.user.model.Role;

/** Who paid, for the summaries. A person's type follows their account. */
public enum PayerType {
    SCHOOL("Schools"),
    PARENT("Parents and carers"),
    /** Practitioners, and school staff paying for themselves. */
    INDIVIDUAL("Practitioners and staff");

    private final String label;

    PayerType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PayerType of(Role personRole) {
        return personRole == Role.PARENT ? PARENT : INDIVIDUAL;
    }
}
