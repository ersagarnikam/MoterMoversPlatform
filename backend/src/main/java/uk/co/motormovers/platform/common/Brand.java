package uk.co.motormovers.platform.common;

/**
 * The names people see, in one place for the API: emails, PDFs, notifications and messages take them from here, so a
 * change of wording is a change to this file. The web app has the same in {@code frontend/src/brand.ts} and the parent
 * app in {@code mobile/src/lib/brand.ts}. Code names (the package, environment variables, the demo domains) are not
 * the brand and stay as they are.
 */
public final class Brand {

    /** The programme's name in full: email subjects and sign-offs, PDF properties, the parent app's store name. */
    public static final String NAME = "Professor Pat Preedy Programme";

    /** The home programme: "the Programme at Home" inside a sentence, "The Programme at Home" at the start of one. */
    public static final String HOME = "Programme at Home";

    /** The team that runs the programme and the platform (the ADMIN role). */
    public static final String TEAM = "Programme team";

    /**
     * What the numbers people quote start with: a certificate's number ("Certificate PPP-12") and the references the
     * team gives invoices ("PPP-1033"; a reference is typed by the team, so this is the convention, not a check).
     * Credit notes keep their own "CN-" numbers.
     */
    public static final String REFERENCE_PREFIX = "PPP-";

    private Brand() {
    }
}
