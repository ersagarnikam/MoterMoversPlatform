package uk.co.motormovers.platform.payment.controller;

import java.net.URI;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.payment.service.OnlinePaymentService;
import uk.co.motormovers.platform.payment.stripe.FakeStripe;

/**
 * The pages of the stand-in for Stripe (development, demos and the browser tests only: this controller exists only
 * with STRIPE_FAKE=true, which production refuses). Opening one does what the person would do on Stripe's page, and
 * the platform hears of it exactly as it would from Stripe, through {@link OnlinePaymentService#receive}.
 */
@RestController
@RequestMapping("/api/dev/stripe")
@ConditionalOnProperty(name = "platform.stripe.fake", havingValue = "true")
public class FakeStripeController {

    private final FakeStripe stripe;
    private final OnlinePaymentService online;

    public FakeStripeController(FakeStripe stripe, OnlinePaymentService online) {
        this.stripe = stripe;
        this.online = online;
    }

    /** The Checkout page: "pays" at once, and goes back to the platform as Stripe does. */
    @GetMapping("/checkout/{pageId}")
    public ResponseEntity<Void> pay(@PathVariable String pageId) {
        FakeStripe.Outcome outcome = stripe.pay(pageId);
        if (outcome == null) {
            return ResponseEntity.notFound().build();
        }
        outcome.messages().forEach(online::receive);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(outcome.back())).build();
    }

    /** In place of Stripe's page for cancelling or changing the card, with what a tester needs beside it. */
    @GetMapping(value = "/portal/{customer}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> portal(@PathVariable String customer) {
        if (!stripe.knows(customer)) {
            return ResponseEntity.notFound().build();
        }
        String here = "/api/dev/stripe/portal/" + customer;
        return ResponseEntity.ok("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>Stand-in for Stripe</title></head><body>"
                + "<h1>Stand-in for Stripe</h1>"
                + "<p>Development only. No card is held and no money moves.</p>"
                + "<p id=\"state\">" + stripe.describe(customer) + "</p>"
                + "<ul>"
                + "<li><a href=\"" + here + "/cancel\">Cancel at the end of the paid month</a></li>"
                + "<li><a href=\"" + here + "/resume\">Do not cancel after all</a></li>"
                + "<li><a href=\"" + here + "/next-month\">The month ends: take the next payment</a></li>"
                + "<li><a href=\"" + here + "/next-month-fails\">The month ends: the card is refused</a></li>"
                + "<li><a href=\"" + here + "/end\">End the subscription now</a></li>"
                + "</ul>"
                + "<p><a href=\"/home\">Back to the platform</a></p>"
                + "</body></html>");
    }

    @GetMapping("/portal/{customer}/{action}")
    public ResponseEntity<Void> act(@PathVariable String customer, @PathVariable String action) {
        List<String> messages = switch (action) {
            case "cancel" -> stripe.cancelAtEnd(customer, true);
            case "resume" -> stripe.cancelAtEnd(customer, false);
            case "next-month" -> stripe.nextMonth(customer);
            case "next-month-fails" -> stripe.nextMonthFails(customer);
            case "end" -> stripe.end(customer);
            default -> null;
        };
        if (messages == null || !stripe.knows(customer)) {
            return ResponseEntity.notFound().build();
        }
        messages.forEach(online::receive);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/api/dev/stripe/portal/" + customer))
                .build();
    }
}
