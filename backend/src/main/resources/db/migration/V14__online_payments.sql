-- Paying by card online (Stripe Checkout). Set when Stripe confirms the payment: the Stripe payment's id, so the
-- team can find it in Stripe for a refund. Null for payments marked paid by hand.
ALTER TABLE payment ADD COLUMN paid_online_ref VARCHAR(255);
