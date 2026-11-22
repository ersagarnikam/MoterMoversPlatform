package uk.co.motormovers.platform.payment.model;

import tools.jackson.databind.JsonNode;

/**
 * Published, in the webhook's transaction, for a signed Stripe message about a subscription, so the Programme at Home
 * can act on it without the payment code knowing it (as with {@link PaymentEvents}).
 */
public final class StripeEvents {

    private StripeEvents() {
    }

    /** One message: Stripe's event type, and the object it is about (an invoice, a subscription, a Checkout page). */
    public static final class Received {

        private final String type;
        private final JsonNode object;
        private boolean changed;

        public Received(String type, JsonNode object) {
            this.type = type;
            this.object = object;
        }

        public String type() {
            return type;
        }

        public JsonNode object() {
            return object;
        }

        /** A listener says it changed something. */
        public void changed() {
            this.changed = true;
        }

        public boolean wasChanged() {
            return changed;
        }
    }
}
