-- An uploaded file's key in the file store, beside the pasted link each item already has. A key wins over a link.
ALTER TABLE programme_session ADD COLUMN video_key VARCHAR(200);
ALTER TABLE home_activity ADD COLUMN video_key VARCHAR(200);
ALTER TABLE induction_lesson ADD COLUMN video_key VARCHAR(200);
ALTER TABLE library_resource ADD COLUMN file_key VARCHAR(200);
