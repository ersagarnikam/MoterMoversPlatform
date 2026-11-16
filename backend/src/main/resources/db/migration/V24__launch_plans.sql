-- The licence plans become Kay's (go-live task 85): two licences, one for a childcare centre or kindergarten and
-- one for a school, and a lower price for groups of schools. The codes stay, so licences and anything else that
-- refers to a plan keep working. The "Full programme" plan is retired: schools that hold it keep it, and no new
-- licence can choose it.
--
-- Prices are not set here, because they differ by country: on start, the API gives each plan still at its
-- "platform setup" price the deployment's launch price (LaunchPrices, from platform.countries.<region>.launch-prices).

UPDATE licence_plan SET label = 'Childcare centre or kindergarten' WHERE code = 'EARLY_YEARS';
UPDATE licence_plan SET label = 'School' WHERE code = 'PRIMARY_CORE';
UPDATE licence_plan SET label = 'Group of schools' WHERE code = 'TRUST';
UPDATE licence_plan SET active = FALSE WHERE code = 'FULL_PROGRAMME';
