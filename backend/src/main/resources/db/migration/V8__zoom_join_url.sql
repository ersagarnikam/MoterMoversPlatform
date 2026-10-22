-- Live sessions can open in Zoom instead of inside the platform (when there is no Meeting SDK app), so each
-- session keeps the meeting's join link that Zoom returns, for learners to open.
ALTER TABLE cohort_session ADD COLUMN zoom_join_url VARCHAR(1000);
