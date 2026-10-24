package uk.co.motormovers.platform.user.model;

/** The six kinds of user on the platform. */
public enum Role {
    /** Class teacher: screens children, plays the daily session, marks participation. */
    TEACHER,
    /** Head teacher or SENCo: progress and participation by class, exports. */
    HEAD,
    /** Academy trust lead: licences and summary results only, never individual children. */
    TRUST_LEAD,
    /** Teacher, TA or therapist on the certification course. */
    PRACTITIONER,
    /** Parent or carer. */
    PARENT,
    /** Pat, Laura and the platform team: content approval, schools, licences, trainers. */
    ADMIN,
    /** Runs live course sessions: the Live sessions page and the register, for the cohorts assigned to them only. */
    TRAINER
}
