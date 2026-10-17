package uk.co.motormovers.platform.family.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.family.service.FamilyService;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.security.service.RateLimiter;

/** The parent app's API. Mapped under /api/family (PARENT only, see SecurityConfig). */
@RestController
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService family;
    private final RateLimiter rateLimiter;
    private final CurrentUserResolver currentUser;

    public FamilyController(FamilyService family, RateLimiter rateLimiter, CurrentUserResolver currentUser) {
        this.family = family;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
    }

    public record RedeemRequest(@NotBlank String code) {
    }

    @GetMapping("/children")
    public List<FamilyViews.LinkedChild> children() {
        return family.children();
    }

    /** Ten codes an hour per account, so codes cannot be guessed. */
    @PostMapping("/children")
    public FamilyViews.LinkedChild redeem(@Valid @RequestBody RedeemRequest request) {
        rateLimiter.check("redeem", String.valueOf(currentUser.current().id()), 10, Duration.ofHours(1));
        return family.redeem(request.code());
    }

    @DeleteMapping("/children/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long childId) {
        family.unlink(childId);
    }

    @GetMapping("/children/{childId}/progress")
    public FamilyViews.Progress progress(@PathVariable Long childId) {
        return family.progress(childId);
    }

    @GetMapping("/home")
    public FamilyViews.Home home(@RequestParam(required = false) ColourStage stage,
            @RequestParam(required = false) Long childId) {
        return family.home(stage, childId);
    }

    @GetMapping("/activities/{id}")
    public FamilyViews.ActivityCard activity(@PathVariable Long id) {
        return family.activity(id);
    }

    @PostMapping("/home/sessions")
    public FamilyViews.SubmitResult recordHome(@RequestBody List<FamilyViews.HomeEntry> entries) {
        return family.recordHome(entries);
    }

    @GetMapping("/guidance")
    public List<FamilyViews.ArticleSummary> guidance(@RequestParam(required = false) ColourStage stage) {
        return family.guidance(stage);
    }

    @GetMapping("/guidance/{id}")
    public FamilyViews.Article article(@PathVariable Long id) {
        return family.article(id);
    }
}
