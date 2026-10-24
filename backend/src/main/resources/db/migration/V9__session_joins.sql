-- When each booked learner opened a live session (pressed Join), so the host sees who has joined during the
-- session. It records opening the session, not how long someone stayed: the register stays the official
-- attendance, and the host can tick everyone who joined in one go.
CREATE TABLE session_join (
    session_id       BIGINT    NOT NULL REFERENCES cohort_session (id) ON DELETE CASCADE,
    enrolment_id     BIGINT    NOT NULL REFERENCES enrolment (id) ON DELETE CASCADE,
    first_joined_at  TIMESTAMP NOT NULL,
    last_joined_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (session_id, enrolment_id)
);
