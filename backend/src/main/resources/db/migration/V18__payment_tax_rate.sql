-- Prices are tax-inclusive (the amount charged). A payment keeps the tax rate in force when it was created, in basis
-- points (2000 = 20%), so a later change of rate leaves it as it was. Rows from before this stay null and show no
-- tax line.
ALTER TABLE payment ADD COLUMN tax_rate_bp INTEGER;
