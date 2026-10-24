-- A training team runs live sessions. Each cohort can name the account of its trainer (a TRAINER or a member of
-- the team), and an account can have its own Zoom login, so each trainer hosts on their own licensed Zoom user and
-- sessions can run at the same time. The free-text trainer_name stays as the name learners see.
ALTER TABLE cohort ADD COLUMN trainer_id BIGINT REFERENCES app_user (id);
ALTER TABLE app_user ADD COLUMN zoom_email VARCHAR(200);
