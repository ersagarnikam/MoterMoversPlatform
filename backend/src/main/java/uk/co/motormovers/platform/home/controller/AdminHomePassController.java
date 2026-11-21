package uk.co.motormovers.platform.home.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.home.dto.HomePassViews;
import uk.co.motormovers.platform.home.service.HomePassService;
import uk.co.motormovers.platform.home.service.HomeSubscriptionService;

/**
 * The team: the Programme at Home's monthly price (and the old pass price), and a family's cover and subscription for
 * support. Under /api/admin (ADMIN only).
 */
@RestController
@RequestMapping("/api/admin/home-pass")
public class AdminHomePassController {

    private final HomePassService passes;
    private final HomeSubscriptionService subscriptions;

    public AdminHomePassController(HomePassService passes, HomeSubscriptionService subscriptions) {
        this.passes = passes;
        this.subscriptions = subscriptions;
    }

    /** The monthly price: dated, and a change applies to new subscriptions only. */
    @GetMapping("/monthly-prices")
    public HomePassViews.Prices monthlyPrices() {
        return subscriptions.prices();
    }

    @PostMapping("/monthly-prices")
    public HomePassViews.Prices changeMonthlyPrice(@Valid @RequestBody HomePassViews.PriceChange change) {
        return subscriptions.changePrice(change);
    }

    /** Cancels a family's subscription for it, recorded with who and why. */
    @PostMapping("/cancel-subscription")
    public HomePassViews.FamilyPass cancelSubscription(@Valid @RequestBody HomePassViews.CancelSubscription request) {
        return subscriptions.cancel(request);
    }

    @GetMapping("/prices")
    public HomePassViews.Prices prices() {
        return passes.prices();
    }

    @PostMapping("/prices")
    public HomePassViews.Prices changePrice(@Valid @RequestBody HomePassViews.PriceChange change) {
        return passes.changePrice(change);
    }

    /** A family's pass, found by the email of any of its parents or carers. */
    @GetMapping("/family")
    public HomePassViews.FamilyPass family(@RequestParam String email) {
        return passes.family(email);
    }

    @PostMapping("/extend")
    public HomePassViews.FamilyPass extend(@Valid @RequestBody HomePassViews.Extend request) {
        return passes.extend(request);
    }
}
