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

/** The team: the Motor Movers at Home pass price, and a family's pass for support. Under /api/admin (ADMIN only). */
@RestController
@RequestMapping("/api/admin/home-pass")
public class AdminHomePassController {

    private final HomePassService passes;

    public AdminHomePassController(HomePassService passes) {
        this.passes = passes;
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
