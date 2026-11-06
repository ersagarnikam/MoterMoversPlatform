-- Induction lessons now have an audience: school staff, or parents on Motor Movers at Home, who must finish theirs
-- before their first home session (Pat, 2026-11-05: a short induction like the staff one). Each audience numbers its
-- own parts, so the column's unique constraint moves to (audience, part); replacing the column removes the old
-- constraint on both PostgreSQL and H2, whose names for it differ.
ALTER TABLE induction_lesson ADD COLUMN audience VARCHAR(10) DEFAULT 'STAFF' NOT NULL;
ALTER TABLE induction_lesson ADD COLUMN part INT;
UPDATE induction_lesson SET part = position;
ALTER TABLE induction_lesson ALTER COLUMN part SET NOT NULL;
ALTER TABLE induction_lesson DROP COLUMN position;
ALTER TABLE induction_lesson ADD CONSTRAINT uq_induction_lesson_part UNIQUE (audience, part);
