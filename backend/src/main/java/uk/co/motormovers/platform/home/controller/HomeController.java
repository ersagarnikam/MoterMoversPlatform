package uk.co.motormovers.platform.home.controller;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.home.dto.HomeViews;
import uk.co.motormovers.platform.home.service.HomeService;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.security.service.RateLimiter;

/**
 * Motor Movers at Home, for the parent app. Mapped under /api/home (PARENT only, see SecurityConfig). A home child's
 * chart answers and exercise ratings also use the assessment endpoints, which check the family through AccessPolicy.
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService home;
    private final RateLimiter rateLimiter;
    private final CurrentUserResolver currentUser;

    public HomeController(HomeService home, RateLimiter rateLimiter, CurrentUserResolver currentUser) {
        this.home = home;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
    }

    /** Open to anyone: the website offers sign-up for families only while the home programme is on. */
    @GetMapping("/available")
    public HomeViews.Availability available() {
        return new HomeViews.Availability(home.isEnabled());
    }

    @GetMapping
    public HomeViews.Overview overview() {
        return home.overview();
    }

    @PostMapping("/children")
    public HomeViews.HomeChild addChild(@RequestBody HomeViews.ChildIn child) {
        return home.addChild(child);
    }

    @PutMapping("/children/{childId}")
    public HomeViews.HomeChild updateChild(@PathVariable Long childId, @RequestBody HomeViews.ChildIn child) {
        return home.updateChild(childId, child);
    }

    @DeleteMapping("/children/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChild(@PathVariable Long childId) {
        home.deleteChild(childId);
    }

    @GetMapping("/children/{childId}")
    public HomeViews.Progress progress(@PathVariable Long childId) {
        return home.progress(childId);
    }

    @GetMapping("/children/{childId}/check")
    public HomeViews.Check check(@PathVariable Long childId) {
        return home.check(childId);
    }

    @PostMapping("/children/{childId}/check")
    public HomeViews.CheckResult submitCheck(@PathVariable Long childId, @RequestBody HomeViews.CheckIn answers) {
        return home.submitCheck(childId, answers);
    }

    @PutMapping("/children/{childId}/stage")
    public HomeViews.HomeChild setStage(@PathVariable Long childId, @RequestBody HomeViews.StageIn stage) {
        return home.setStage(childId, stage == null ? null : stage.stage());
    }

    /** Works for a home child and for a school child the parent is linked to. */
    @GetMapping("/children/{childId}/today")
    public HomeViews.Today today(@PathVariable Long childId) {
        return home.today(childId);
    }

    @PostMapping("/sessions")
    public HomeViews.SubmitResult recordSessions(@RequestBody List<HomeViews.SessionIn> sessions) {
        return home.recordSessions(sessions);
    }

    /** Optionally emails the code; twenty emails a day per account, so the platform cannot be used to send mail. */
    @PostMapping("/invites")
    public HomeViews.Invite invite(@Valid @RequestBody(required = false) HomeViews.InviteIn in) {
        String email = in == null || in.email() == null || in.email().isBlank() ? null : in.email();
        if (email != null) {
            rateLimiter.check("home-invite-email", String.valueOf(currentUser.current().id()), 20, Duration.ofDays(1));
        }
        return home.invite(email);
    }

    /** Ten codes an hour per account, so codes cannot be guessed. */
    @PostMapping("/join")
    public HomeViews.Overview join(@RequestBody HomeViews.CodeIn code) {
        rateLimiter.check("home-join", String.valueOf(currentUser.current().id()), 10, Duration.ofHours(1));
        return home.join(code == null ? null : code.code());
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long userId) {
        home.removeMember(userId);
    }

    /** The school's parent code; shares the home record with the school. Rate limited like other parent codes. */
    @PostMapping("/children/{childId}/join-school")
    public FamilyViews.LinkedChild joinSchool(@PathVariable Long childId, @RequestBody HomeViews.CodeIn code) {
        rateLimiter.check("redeem", String.valueOf(currentUser.current().id()), 10, Duration.ofHours(1));
        return home.joinSchool(childId, code == null ? null : code.code());
    }
}
