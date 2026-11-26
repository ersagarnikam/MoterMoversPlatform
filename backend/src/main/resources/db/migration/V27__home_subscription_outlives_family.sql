-- Clean-up before the feature freeze (go-live task 88).

-- A subscription's row now outlives its family: when the last member leaves, the family is deleted and the row stays,
-- ENDED and with no family, so the team can still find it by the payer's email. The column is replaced rather than
-- altered, because the foreign key's name differs between H2 and PostgreSQL (as in V19).
DROP INDEX home_subscription_family;
ALTER TABLE home_subscription ADD COLUMN family_ref BIGINT REFERENCES family (id) ON DELETE SET NULL;
UPDATE home_subscription SET family_ref = family_id;
ALTER TABLE home_subscription DROP COLUMN family_id;
ALTER TABLE home_subscription RENAME COLUMN family_ref TO family_id;
CREATE INDEX home_subscription_family ON home_subscription (family_id);
CREATE INDEX home_subscription_payer ON home_subscription (payer_id);

-- The last day it has paid for, kept on the row because the days themselves (home_pass) go with the family.
ALTER TABLE home_subscription ADD COLUMN paid_to DATE;
UPDATE home_subscription s SET paid_to =
    (SELECT MAX(p.ends_on) FROM home_pass p WHERE p.subscription_id = s.id);

-- The last day the family was told its subscription ends on, so it is told once for each last day: cancelling,
-- carrying on and cancelling again in the same month sends one email, not two.
ALTER TABLE home_subscription ADD COLUMN end_notice_day DATE;
